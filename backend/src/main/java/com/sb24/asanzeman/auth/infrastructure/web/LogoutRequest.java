package com.sb24.asanzeman.auth.infrastructure.web;

/** Optional body of {@code POST /api/auth/logout}: the refresh token to revoke along with the access token. */
public record LogoutRequest(String refreshToken) {
}
