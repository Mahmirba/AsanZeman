package com.sb24.asanzeman.request.domain.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Registers an issuance request for a customer (SRS UC-AZS-8, D52).
 *
 * @param agencyId     the logged-in agency
 * @param sessionId    its Asan Zeman session ({@code sid}), which finds its own Yaghut session (D35)
 * @param registeredBy the registering user's name, for the audit trail
 * @param nationalCode the customer's national code, as typed
 * @param dueDate      تاریخ سررسید, already Gregorian
 * @param clientIp     the caller's address, for the audit log; may be {@code null}
 */
public record RegisterRequestCommand(UUID agencyId, UUID sessionId, String registeredBy, String nationalCode,
        String branchCode, long amount, LocalDate dueDate, List<CompanionInput> companions, String clientIp,
        String userAgent) {

    public RegisterRequestCommand {
        companions = companions == null ? List.of() : List.copyOf(companions);
    }
}
