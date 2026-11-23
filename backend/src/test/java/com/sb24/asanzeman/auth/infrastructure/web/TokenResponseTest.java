package com.sb24.asanzeman.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TokenResponseTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void lifetimesAreSecondsFromNow() {
        var response = TokenResponse.of(new IssuedTokens("a", NOW.plusSeconds(900), "r", NOW.plusSeconds(3600)),
                new MutableClock(NOW));

        assertThat(response).isEqualTo(new TokenResponse("a", "Bearer", 900, "r", 3600));
    }

    @Test
    void aSessionWithoutARefreshTokenHasNoRefreshLifetime() {
        var response = TokenResponse.of(new IssuedTokens("a", NOW.plusSeconds(1800), null, NOW.plusSeconds(1800)),
                new MutableClock(NOW));

        assertThat(response).isEqualTo(new TokenResponse("a", "Bearer", 1800, null, 0));
    }

    @Test
    void aLifetimeThatAlreadyEndedIsZeroNotNegative() {
        var response = TokenResponse.of(new IssuedTokens("a", NOW.minusSeconds(5), "r", NOW.minusSeconds(1)),
                new MutableClock(NOW));

        assertThat(response.expiresIn()).isZero();
        assertThat(response.refreshExpiresIn()).isZero();
    }
}
