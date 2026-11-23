package com.sb24.asanzeman.auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Body of {@code POST /api/auth/customer/otp/verify}: the challenge from the login and the code the customer got. */
public record OtpVerifyRequest(@NotNull UUID challengeId, @NotBlank String code) {
}
