package com.sb24.asanzeman.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/agency/login}: the agency user's Yaghut credentials and the ARcaptcha token (6.5). */
public record AgencyLoginRequest(@NotBlank String username, @NotBlank String password,
        @NotBlank String arcaptchaToken) {
}
