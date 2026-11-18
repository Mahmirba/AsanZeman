package com.sb24.asanzeman.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SessionAndRecordsTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void aSessionKeepsItsFields() {
        var session = new Session(ID, "ali", Role.BRANCH, "0123");

        assertThat(session.subjectId()).isEqualTo(ID);
        assertThat(session.subjectName()).isEqualTo("ali");
        assertThat(session.role()).isEqualTo(Role.BRANCH);
        assertThat(session.branchCode()).isEqualTo("0123");
        assertThat(new Session(ID, "agency", Role.AGENCY, null).branchCode()).isNull();
        assertThat(Role.values()).containsExactly(Role.BRANCH, Role.ADMIN, Role.AGENCY, Role.CUSTOMER);
    }

    @Test
    void aSessionNeedsAnIdANameAndARole() {
        assertThatThrownBy(() -> new Session(null, "ali", Role.BRANCH, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Session(ID, null, Role.BRANCH, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Session(ID, "ali", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aBlankSessionNameIsRefused(String name) {
        assertThatThrownBy(() -> new Session(ID, name, Role.BRANCH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clientInfoCanBeUnknown() {
        assertThat(ClientInfo.unknown()).isEqualTo(new ClientInfo(null, null));
    }

    @Test
    void aRefreshTokenKnowsItsState() {
        var session = new Session(ID, "ali", Role.BRANCH, "1");
        var fresh = new RefreshTokenRecord(ID, ID, session, "h", NOW.plusSeconds(60), null, null);
        var used = new RefreshTokenRecord(ID, ID, session, "h", NOW.plusSeconds(60), NOW, null);
        var revoked = new RefreshTokenRecord(ID, ID, session, "h", NOW.plusSeconds(60), null, NOW);

        assertThat(fresh.used()).isFalse();
        assertThat(fresh.revoked()).isFalse();
        assertThat(used.used()).isTrue();
        assertThat(revoked.revoked()).isTrue();
        assertThat(fresh.expired(NOW)).isFalse();
        assertThat(fresh.expired(NOW.plusSeconds(60))).isTrue();
    }

    @Test
    void theLoginErrorsHaveStableCodesAndStatuses() {
        assertThat(new InvalidSessionException()).extracting(DomainException::code, DomainException::kind)
                .containsExactly("auth.session-invalid", DomainException.Kind.UNAUTHORIZED);
        assertThat(new InvalidBankCredentialsException()).extracting(DomainException::code, DomainException::kind)
                .containsExactly("auth.bank.invalid-credentials", DomainException.Kind.UNAUTHORIZED);
        assertThat(new BankAccessDeniedException()).extracting(DomainException::code, DomainException::kind)
                .containsExactly("auth.bank.no-access", DomainException.Kind.FORBIDDEN);
        assertThat(new PersonnelNotFoundException()).extracting(DomainException::code, DomainException::kind)
                .containsExactly("auth.bank.personnel-not-found", DomainException.Kind.FORBIDDEN);
    }

    @Test
    void anIssuedTokenPairAndAnAppUserKeepTheirFields() {
        var tokens = new IssuedTokens("a", NOW, "r", NOW.plusSeconds(5));
        var user = new AppUser(ID, new AccountName("ali"), "0123", Role.ADMIN);

        assertThat(tokens.accessToken()).isEqualTo("a");
        assertThat(tokens.refreshTokenExpiresAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(user.role()).isEqualTo(Role.ADMIN);
    }
}
