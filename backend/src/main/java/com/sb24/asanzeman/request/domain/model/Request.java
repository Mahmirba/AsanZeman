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
     * The agency's «ثبت درخواست ابطال» action (4.2, D11). Before «اقدام» the request is simply cancelled.
     *
     * @throws RevocationNotAvailableException the branch has already started: only a revocation request can undo it
     * @throws RequestNotCancellableException  already rejected, cancelled, revoked or completed, or itself a revocation
     */
    public Request cancelByAgency() {
        if (type != RequestType.ISSUANCE) {
            throw new RequestNotCancellableException();
        }
        return switch (status) {
            case REGISTERED_AT_AGENCY, PENDING_BRANCH_REVIEW -> withStatus(RequestStatus.CANCELLED);
            case UNDER_REVIEW, UNDER_ACTION, ISSUED -> throw new RevocationNotAvailableException();
            case REJECTED, CANCELLED, REVOKED, COMPLETED -> throw new RequestNotCancellableException();
        };
    }

    /** The customer's inquiries run while an issuance request is still at the agency's step (5.1). */
    public boolean canBeInquired() {
        return type == RequestType.ISSUANCE && status == RequestStatus.REGISTERED_AT_AGENCY;
    }

    /**
     * A negative inquiry rejects the request (D3, 5.1).
     *
     * @throws RequestNotInquirableException the request is no longer waiting for its inquiries
     */
    public Request rejectedByInquiry() {
        if (!canBeInquired()) {
            throw new RequestNotInquirableException();
        }
        return withStatus(RequestStatus.REJECTED);
    }

    /**
     * The customer's final submit (SRS step 15): the request goes to the branch, «در انتظار بررسی». The caller has
     * checked that the inquiries passed and the collateral was chosen.
     *
     * @throws RequestNotSubmittableException the request is not waiting at the agency's step
     */
    public Request submitted() {
        if (!canBeInquired()) {
            throw new RequestNotSubmittableException("the request is " + status);
        }
        return withStatus(RequestStatus.PENDING_BRANCH_REVIEW);
    }

    private Request withStatus(RequestStatus newStatus) {
        return new Request(id, trackingCode, type, newStatus, agencyId, branchCode, amount, dueDate, customer,
                companions, registeredBy, registeredAt);
    }

    /**
     * Registers an issuance request (SRS UC-AZS-8).
     *
     * @param today the registration day, for the 18-year check
     * @throws CustomerNotActualException      the customer is not a natural person
     * @throws CustomerUnderageException       the customer is under 18
     * @throws CustomerMobileMissingException  the bank has no mobile number for the customer
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
        customer.requireEligible(today);
        if (customer.mobile() == null || customer.mobile().isBlank()) {
            throw new CustomerMobileMissingException();
        }
        return new Request(id, trackingCode, RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY, agencyId,
                branchCode, amount, dueDate, customer, companions, registeredBy, registeredAt);
    }
}
