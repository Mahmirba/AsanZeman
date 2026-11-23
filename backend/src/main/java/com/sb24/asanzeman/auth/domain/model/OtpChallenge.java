package com.sb24.asanzeman.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One SMS code sent to a customer at login (6.3, D34). The code itself is the Notifier's; here is only the id it gave
 * ({@code otpId}), the customer, the session family the Yaghut session was kept under, and whether it was used.
 */
public record OtpChallenge(UUID id, String nationalCode, String mobile, String otpId, UUID familyId, Instant sentAt,
        boolean verified) {
}
