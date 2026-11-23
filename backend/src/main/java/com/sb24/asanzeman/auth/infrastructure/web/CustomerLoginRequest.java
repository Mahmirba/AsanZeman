package com.sb24.asanzeman.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/customer/login}: the customer's Yaghut credentials and the ARcaptcha token (6.3). */
public record CustomerLoginRequest(@NotBlank String username, @NotBlank String password,
        @NotBlank String arcaptchaToken) {
}
