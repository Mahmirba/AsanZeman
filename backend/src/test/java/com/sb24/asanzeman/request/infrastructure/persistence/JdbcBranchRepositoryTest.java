package com.sb24.asanzeman.request.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.BranchData;
import com.sb24.asanzeman.request.domain.model.BranchSyncResult;
import com.sb24.asanzeman.support.H2Auth;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2. */
class JdbcBranchRepositoryTest {

    private static final Instant T1 = Instant.parse("2026-11-20T02:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-21T02:00:00Z");

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcBranchRepository repository = new JdbcBranchRepository(jdbc);

    private static BranchData branch(String code, String name) {
        return new BranchData(code, name, "Tehran", "addr", "tel", "fax", "zip");
    }

    private String text(String column, String code) {
        return jdbc.sql("SELECT " + column + " FROM azs_business.branch WHERE branch_code = :c").param("c", code)
                .query(String.class).single();
    }

    @Test
    void aNewBranchIsInsertedActiveAndItsOwnParent() {
        BranchSyncResult result = repository.apply(List.of(branch("0123", "Main")), T1);

        assertThat(result).isEqualTo(new BranchSyncResult(1, 0, 0));
        assertThat(text("name", "0123")).isEqualTo("Main");
        assertThat(text("parent_branch", "0123")).isEqualTo("0123");
        assertThat(text("active", "0123")).isEqualTo("1");
        assertThat(repository.any()).isTrue();
    }

    @Test
    void anEmptyTableHasNoBranches() {
        assertThat(repository.any()).isFalse();
    }

    @Test
    void anExistingBranchIsUpdatedButItsReplacementIsNeverTouched() {
        repository.apply(List.of(branch("0123", "Main"), branch("0456", "Other")), T1);
        // a DBA script closed 0123 in favour of 0456 (4.4)
        jdbc.sql("UPDATE azs_business.branch SET parent_branch = '0456' WHERE branch_code = '0123'").update();

        BranchSyncResult result = repository.apply(List.of(branch("0123", "Main renamed"), branch("0456", "Other")), T2);

        assertThat(result).isEqualTo(new BranchSyncResult(0, 2, 0));
        assertThat(text("name", "0123")).isEqualTo("Main renamed");
        assertThat(text("parent_branch", "0123")).isEqualTo("0456");
    }

    @Test
    void aBranchMissingFromYaghutIsDeactivatedNotDeleted() {
        repository.apply(List.of(branch("0123", "Main"), branch("0456", "Other")), T1);

        BranchSyncResult result = repository.apply(List.of(branch("0123", "Main")), T2);

        assertThat(result).isEqualTo(new BranchSyncResult(0, 1, 1));
        assertThat(text("active", "0456")).isEqualTo("0");
        assertThat(text("name", "0456")).isEqualTo("Other");
    }

    @Test
    void anInactiveBranchThatIsStillMissingIsNotCountedAgain() {
        repository.apply(List.of(branch("0123", "Main"), branch("0456", "Other")), T1);
        repository.apply(List.of(branch("0123", "Main")), T2);

        BranchSyncResult result = repository.apply(List.of(branch("0123", "Main")), T2.plusSeconds(60));

        assertThat(result).isEqualTo(new BranchSyncResult(0, 1, 0));
    }

    @Test
    void aBranchThatComesBackIsReactivated() {
        repository.apply(List.of(branch("0123", "Main"), branch("0456", "Other")), T1);
        repository.apply(List.of(branch("0123", "Main")), T2);

        repository.apply(List.of(branch("0123", "Main"), branch("0456", "Other")), T2.plusSeconds(60));

        assertThat(text("active", "0456")).isEqualTo("1");
    }

    @Test
    void theCodeKeepsItsLeadingZero() {
        repository.apply(List.of(branch("0007", "Seven")), T1);

        assertThat(text("branch_code", "0007")).isEqualTo("0007");
    }
}
