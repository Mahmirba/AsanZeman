package com.sb24.asanzeman.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/refresh}: the refresh token travels in the body, never in a cookie (D7). */
public record RefreshRequest(@NotBlank String refreshToken) {
}
