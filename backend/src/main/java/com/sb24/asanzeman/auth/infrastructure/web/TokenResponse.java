package com.sb24.asanzeman.auth.infrastructure.web;

import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The tokens as the panel receives them. {@code expiresIn} and {@code refreshExpiresIn} are seconds from now; the
 * refresh token is rotated on every use (D7).
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
        long refreshExpiresIn) {

    static TokenResponse of(IssuedTokens tokens, Clock clock) {
        Instant now = clock.instant();
        return new TokenResponse(tokens.accessToken(), "Bearer", secondsUntil(now, tokens.accessTokenExpiresAt()),
                tokens.refreshToken(),
                tokens.refreshToken() == null ? 0 : secondsUntil(now, tokens.refreshTokenExpiresAt()));
    }

    private static long secondsUntil(Instant now, Instant end) {
        return Math.max(0, Duration.between(now, end).getSeconds());
    }
}
