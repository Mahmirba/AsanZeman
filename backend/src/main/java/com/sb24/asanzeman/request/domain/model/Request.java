package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A request for a travel-return guarantee (4, D52). Registration (Phase 2) makes an {@code ISSUANCE} request in
 * {@code REGISTERED_AT_AGENCY}; the other transitions are added with their phases. The record is immutable: a
 * transition returns a new instance.
 *
 * @param agencyId     the registering agency
 * @param branchCode   the branch chosen, as Yaghut writes it
 * @param amount       the guarantee amount in rials, 1 to 16 digits
 * @param dueDate      تاریخ سررسید; information only, nothing happens when it passes (D19)
 * @param registeredBy the registering user's name, for the audit trail
 */
public record Request(
        UUID id,
        TrackingCode trackingCode,
        RequestType type,
        RequestStatus status,
        UUID agencyId,
        String branchCode,
        Rials amount,
        LocalDate dueDate,
        CustomerSnapshot customer,
        List<Companion> companions,
        String registeredBy,
        Instant registeredAt) {

    public static final int MAX_COMPANIONS = 5;
    public static final long MAX_AMOUNT = 9_999_999_999_999_999L;
    public static final int MIN_CUSTOMER_AGE = 18;

    public Request {
        companions = List.copyOf(companions);
    }

    /**
     * Registers an issuance request (SRS UC-AZS-8).
     *
     * @param today the registration day, for the 18-year check
     * @throws CustomerNotActualException      the customer is not a natural person
     * @throws CustomerUnderageException       the customer is under 18
     * @throws InvalidRequestDataException     a field breaks an SRS rule
     * @throws TemporarilyUnavailableException Yaghut gave no birth date, so the age cannot be checked
     */
    public static Request register(UUID id, TrackingCode trackingCode, UUID agencyId, String branchCode, Rials amount,
            LocalDate dueDate, CustomerSnapshot customer, List<Companion> companions, String registeredBy,
            Instant registeredAt, LocalDate today) {
        if (branchCode == null || branchCode.isBlank()) {
            throw new InvalidRequestDataException("a branch is required");
        }
        if (amount == null || amount.isZero() || amount.value() > MAX_AMOUNT) {
            throw new InvalidRequestDataException("the amount must be 1 to 16 digits");
        }
        if (dueDate == null) {
            throw new InvalidRequestDataException("the due date is required");
        }
        if (companions.size() > MAX_COMPANIONS) {
            throw new InvalidRequestDataException("at most " + MAX_COMPANIONS + " companions");
        }
        if (!customer.isActual()) {
            throw new CustomerNotActualException();
        }
        if (customer.birthDate() == null) {
            throw new TemporarilyUnavailableException("Yaghut",
                    new IllegalStateException("customerDetailInfo gave no birth date"));
        }
        if (customer.ageOn(today) < MIN_CUSTOMER_AGE) {
            throw new CustomerUnderageException();
        }
        return new Request(id, trackingCode, RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY, agencyId,
                branchCode, amount, dueDate, customer, companions, registeredBy, registeredAt);
    }
}
