package com.sb24.asanzeman.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.sb24.asanzeman.auth.domain.model.AccountName;
import com.sb24.asanzeman.auth.domain.model.AppUser;
import com.sb24.asanzeman.auth.domain.model.Personnel;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.support.H2Auth;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2; the Oracle migration itself is checked by FlywayMigrationIT. */
class JdbcAppUserRepositoryTest {

    private static final Instant T1 = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-18T08:30:00Z");

    private JdbcClient jdbc;
    private JdbcAppUserRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = H2Auth.newDatabase();
        repository = new JdbcAppUserRepository(jdbc);
    }

    private static Personnel personnel(String name, String branch, String branchName) {
        return new Personnel(new AccountName(name), branch, "P1", name + ".u", branchName, "Manager", "Clerk");
    }

    private Map<String, Object> row(String name) {
        return jdbc.sql("SELECT * FROM azs_auth.app_user WHERE account_name = ?").param(name).query().singleRow();
    }

    @Test
    void theFirstLoginCreatesTheUser() {
        AppUser user = repository.upsert(personnel("ali", "0123", "Main"), Role.BRANCH, T1);

        assertThat(user.id()).isNotNull();
        assertThat(user.accountName().value()).isEqualTo("ali");
        assertThat(user.branchCode()).isEqualTo("0123");
        assertThat(user.role()).isEqualTo(Role.BRANCH);
        Map<String, Object> row = row("ali");
        assertThat(row).containsEntry("BRANCH_CODE", "0123").containsEntry("BRANCH_NAME", "Main")
                .containsEntry("PRSN_CODE", "P1").containsEntry("PERSONEL_USER_NAME", "ali.u")
                .containsEntry("JOB_POSITION", "Manager").containsEntry("JOB", "Clerk")
                .containsEntry("ROLE", "BRANCH");
    }

    @Test
    void everyLaterLoginUpdatesTheSameUserWithTheNewBranchAndRole() {
        AppUser first = repository.upsert(personnel("ali", "0123", "Main"), Role.BRANCH, T1);

        AppUser second = repository.upsert(personnel("ali", "0456", "Other"), Role.ADMIN, T2);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.branchCode()).isEqualTo("0456");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM azs_auth.app_user").query(Integer.class).single()).isEqualTo(1);
        assertThat(row("ali")).containsEntry("BRANCH_CODE", "0456").containsEntry("BRANCH_NAME", "Other")
                .containsEntry("ROLE", "ADMIN");
    }

    @Test
    void differentUsersGetDifferentIds() {
        AppUser ali = repository.upsert(personnel("ali", "1", "A"), Role.BRANCH, T1);
        AppUser reza = repository.upsert(personnel("reza", "1", "A"), Role.BRANCH, T1);

        assertThat(reza.id()).isNotEqualTo(ali.id());
    }

    @Test
    void optionalDetailsMayBeMissing() {
        var minimal = new Personnel(new AccountName("sara"), "9", null, null, null, null, null);

        repository.upsert(minimal, Role.BRANCH, T1);

        assertThat(row("sara")).containsEntry("BRANCH_CODE", "9").containsEntry("PRSN_CODE", null);
    }

    @Test
    void twoFirstLoginsAtOnceEndUpAsOneUser() {
        // the second call looked, found nobody, and inserted after the first one did: the update takes over
        repository.upsert(personnel("ali", "0123", "Main"), Role.BRANCH, T1);
        JdbcAppUserRepository racing = spy(repository);
        doReturn(Optional.empty()).doCallRealMethod().when(racing).findId("ali");

        AppUser user = racing.upsert(personnel("ali", "0456", "Other"), Role.ADMIN, T2);

        assertThat(jdbc.sql("SELECT COUNT(*) FROM azs_auth.app_user").query(Integer.class).single()).isEqualTo(1);
        assertThat(user.branchCode()).isEqualTo("0456");
        assertThat(row("ali")).containsEntry("BRANCH_CODE", "0456").containsEntry("ROLE", "ADMIN");
    }

    @Test
    void ifTheRowVanishesAfterADuplicateTheOriginalErrorSurfaces() {
        repository.upsert(personnel("ali", "0123", "Main"), Role.BRANCH, T1);
        JdbcAppUserRepository racing = spy(repository);
        doReturn(Optional.empty()).when(racing).findId("ali");

        assertThatThrownBy(() -> racing.upsert(personnel("ali", "0456", "Other"), Role.ADMIN, T2))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
