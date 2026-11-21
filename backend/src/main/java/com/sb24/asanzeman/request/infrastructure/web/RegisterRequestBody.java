package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.shared.validation.IranianNationalCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/agency/requests} (SRS UC-AZS-8, D52). Dates are Jalali {@code yyyy/MM/dd}, as the SRS
 * shows them. The amount is in rials, at most 16 digits.
 */
public record RegisterRequestBody(
        @NotBlank @IranianNationalCode String nationalCode,
        @NotBlank String branchCode,
        @Positive @Max(9_999_999_999_999_999L) long amount,
        @NotBlank String dueDate,
        @Size(max = 5) @Valid List<CompanionBody> companions) {

    /** A companion: every field is required. */
    public record CompanionBody(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @IranianNationalCode String nationalCode,
            @NotBlank String birthDate,
            @NotBlank @Size(max = 30) String passportNumber) {
    }
}
