package com.sb24.asanzeman.auth.domain.model;

import java.time.Instant;

/**
 * The tokens handed to a panel after login or refresh (D5, D7): a short-lived access token and an opaque refresh
 * token. Only a hash of the refresh token is stored.
 */
public record IssuedTokens(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
        Instant refreshTokenExpiresAt) {
}
