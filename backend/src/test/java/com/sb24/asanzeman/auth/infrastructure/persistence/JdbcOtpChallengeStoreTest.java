package com.sb24.asanzeman.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.auth.domain.model.OtpChallenge;
import com.sb24.asanzeman.support.H2Auth;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2. */
class JdbcOtpChallengeStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final Duration LOCKOUT = Duration.ofMinutes(15);
    private static final String CODE = "1000000011";

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcOtpChallengeStore store = new JdbcOtpChallengeStore(jdbc);

    private OtpChallenge challenge() {
        return new OtpChallenge(UUID.randomUUID(), CODE, "09121234567", "otp-1", UUID.randomUUID(), NOW, false);
    }

    @Test
    void aSavedChallengeIsFoundWithAllItsDetails() {
        OtpChallenge saved = challenge();

        store.save(saved);

        assertThat(store.find(saved.id())).contains(saved);
    }

    @Test
    void onlyTheCodesSentBeforeTheLimitAreDeleted() {
        OtpChallenge old = new OtpChallenge(UUID.randomUUID(), CODE, "09121234567", "otp-0", UUID.randomUUID(),
                NOW.minusSeconds(100), false);
        OtpChallenge fresh = challenge();
        store.save(old);
        store.save(fresh);

        assertThat(store.deleteSentBefore(NOW)).isEqualTo(1);

        assertThat(store.find(old.id())).isEmpty();
        assertThat(store.find(fresh.id())).isPresent();
        assertThat(store.deleteSentBefore(NOW)).isZero();
    }

    @Test
    void anUnknownChallengeIsNotFound() {
        assertThat(store.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aCodeCanBeUsedOnlyOnce() {
        OtpChallenge saved = challenge();
        store.save(saved);

        assertThat(store.markVerified(saved.id())).isTrue();
        assertThat(store.markVerified(saved.id())).isFalse();
        assertThat(store.find(saved.id()).orElseThrow().verified()).isTrue();
        assertThat(store.markVerified(UUID.randomUUID())).isFalse();
    }

    @Test
    void wrongCodesLockTheCustomerAtTheLimitAndTheCountStartsAgain() {
        assertThat(store.recordWrong(CODE, NOW, 3, LOCKOUT)).isFalse();
        assertThat(store.recordWrong(CODE, NOW, 3, LOCKOUT)).isFalse();
        assertThat(store.isLocked(CODE, NOW)).isFalse();

        assertThat(store.recordWrong(CODE, NOW, 3, LOCKOUT)).isTrue();

        assertThat(store.isLocked(CODE, NOW)).isTrue();
        assertThat(store.isLocked(CODE, NOW.plus(LOCKOUT).minusSeconds(1))).isTrue();
        assertThat(store.isLocked(CODE, NOW.plus(LOCKOUT))).isFalse();
        assertThat(jdbc.sql("SELECT wrong_attempts FROM azs_auth.otp_lockout WHERE national_code = :c")
                .param("c", CODE).query(Integer.class).single()).isZero();
    }

    @Test
    void theCountBelongsToTheCustomerNotToTheirCodes() {
        store.recordWrong(CODE, NOW, 3, LOCKOUT);
        store.recordWrong("1000000028", NOW, 3, LOCKOUT);

        assertThat(jdbc.sql("SELECT wrong_attempts FROM azs_auth.otp_lockout WHERE national_code = :c")
                .param("c", CODE).query(Integer.class).single()).isEqualTo(1);
        assertThat(store.isLocked("1000000028", NOW)).isFalse();
    }

    @Test
    void aRightCodeEndsTheCount() {
        store.recordWrong(CODE, NOW, 3, LOCKOUT);
        store.recordWrong(CODE, NOW, 3, LOCKOUT);

        store.clearWrong(CODE);

        assertThat(store.recordWrong(CODE, NOW, 3, LOCKOUT)).isFalse();
        assertThat(store.recordWrong(CODE, NOW, 3, LOCKOUT)).isFalse();
    }

    @Test
    void anUnknownCustomerIsNotLockedAndClearingThemIsFine() {
        assertThat(store.isLocked("1000000044", NOW)).isFalse();
        store.clearWrong("1000000044");
    }
}
