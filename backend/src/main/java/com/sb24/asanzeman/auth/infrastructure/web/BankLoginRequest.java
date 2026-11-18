package com.sb24.asanzeman.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/bank/login}: what the user typed and the one-time ARcaptcha token (6.5). */
public record BankLoginRequest(@NotBlank String username, @NotBlank String password,
        @NotBlank String arcaptchaToken) {
}
