package com.sb24.asanzeman.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.support.H2Auth;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real SQL on in-memory H2. */
class JdbcJwtBlacklistTest {

    private JdbcJwtBlacklist blacklist;

    @BeforeEach
    void setUp() {
        blacklist = new JdbcJwtBlacklist(H2Auth.newDatabase());
    }

    @Test
    void aRevokedTokenIsReportedRevoked() {
        blacklist.revoke("jti-1", Instant.parse("2026-11-17T10:15:00Z"), "LOGOUT");

        assertThat(blacklist.isRevoked("jti-1")).isTrue();
    }

    @Test
    void otherTokensAreNotRevoked() {
        blacklist.revoke("jti-1", Instant.parse("2026-11-17T10:15:00Z"), "LOGOUT");

        assertThat(blacklist.isRevoked("jti-2")).isFalse();
    }

    @Test
    void revokingTwiceIsHarmless() {
        blacklist.revoke("jti-1", Instant.parse("2026-11-17T10:15:00Z"), "LOGOUT");
        blacklist.revoke("jti-1", Instant.parse("2026-11-17T10:15:00Z"), "LOGOUT");

        assertThat(blacklist.isRevoked("jti-1")).isTrue();
    }
}
