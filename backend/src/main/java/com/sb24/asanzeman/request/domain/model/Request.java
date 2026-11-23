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

    /** The customer did not complete the request in time: it is rejected (7-day rule, 4.2). */
    public Request expired() {
        if (!canBeInquired()) {
            throw new RequestNotInquirableException();
        }
        return withStatus(RequestStatus.REJECTED);
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

    // ---- the branch's work (4.1, 4.2): the same steps for an issuance and a revocation request -----------------------

    /** «اقدام»: the branch user takes the request. */
    Request reviewStarted() {
        return step(RequestStatus.PENDING_BRANCH_REVIEW, RequestStatus.UNDER_REVIEW);
    }

    /** «تایید درخواست»: the branch goes on to the digital signature and Farangin. */
    Request approvedForAction() {
        return step(RequestStatus.UNDER_REVIEW, RequestStatus.UNDER_ACTION);
    }

    /** «رد درخواست». */
    Request rejectedByBranch() {
        return step(RequestStatus.UNDER_REVIEW, RequestStatus.REJECTED);
    }

    /** The guarantee number was entered: «صادر شده». Only an issuance request is issued. */
    Request issued() {
        requireType(RequestType.ISSUANCE);
        return step(RequestStatus.UNDER_ACTION, RequestStatus.ISSUED);
    }

    /** The branch revoked the guarantee in Farangin and confirmed it: the revocation request is done. */
    Request revocationCompleted() {
        requireType(RequestType.REVOCATION);
        return step(RequestStatus.UNDER_ACTION, RequestStatus.COMPLETED);
    }

    /**
     * The revocation of this issuance request was completed (4.2): it is revoked, whether it was issued or still being
     * worked on.
     */
    Request revoked() {
        requireType(RequestType.ISSUANCE);
        if (status != RequestStatus.ISSUED && status != RequestStatus.UNDER_REVIEW
                && status != RequestStatus.UNDER_ACTION) {
            throw new RequestNotReviewableException("the request is " + status + ", it cannot be revoked");
        }
        return withStatus(RequestStatus.REVOKED);
    }

    /** «انصراف» or «لغو اقدام», an auto-release or an admin release: back to the queue. */
    Request releasedToBranch() {
        if (status != RequestStatus.UNDER_REVIEW && status != RequestStatus.UNDER_ACTION) {
            throw new RequestNotReviewableException("the request is " + status + ", nobody is working on it");
        }
        return withStatus(RequestStatus.PENDING_BRANCH_REVIEW);
    }

    /**
     * From «اقدام» on, the agency's one action cannot cancel the request any more: it asks for a revocation (4.2, D11).
     * That is an issuance request the branch has taken, or has issued.
     */
    public boolean needsRevocation() {
        return type == RequestType.ISSUANCE && (status == RequestStatus.UNDER_REVIEW
                || status == RequestStatus.UNDER_ACTION || status == RequestStatus.ISSUED);
    }

    /**
     * The revocation request for this issuance request (4.2): the same customer, guarantee and branch, with its own
     * tracking code. It waits in the branch's queue, or, when the issuance request is mid-work, goes straight to the
     * user who holds it (D17), which the caller says with {@code status}.
     *
     * @param registeredBy the agency user who asked for it
     * @throws RequestNotCancellableException nothing started, or already finished: there is nothing to revoke
     */
    public Request requestRevocation(UUID revocationId, TrackingCode revocationCode, RequestStatus status,
            String registeredBy, Instant at) {
        if (!needsRevocation()) {
            throw new RequestNotCancellableException();
        }
        return new Request(revocationId, revocationCode, RequestType.REVOCATION, status, agencyId, branchCode, amount,
                dueDate, customer, companions, registeredBy, at);
    }

    private void requireType(RequestType required) {
        if (type != required) {
            throw new RequestNotReviewableException("this step is for a " + required + " request, not " + type);
        }
    }

    private Request step(RequestStatus from, RequestStatus to) {
        if (status != from) {
            throw new RequestNotReviewableException("the request is " + status + ", not " + from);
        }
        return withStatus(to);
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
