package com.sb24.asanzeman.request.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the agency's tracking list (SRS UC-AZS-13). The columns about the branch's decision, the customer's
 * completion and the inquiries are added with the phases that produce them.
 */
public record RequestSummary(UUID id, String trackingCode, RequestType type, RequestStatus status,
        String customerCode, String customerCif, String customerName, long amount, String branchCode,
        String branchName, LocalDate dueDate, Instant registeredAt, String agencyName, String customerMobile) {
}
