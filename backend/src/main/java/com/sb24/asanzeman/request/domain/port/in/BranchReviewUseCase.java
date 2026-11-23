package com.sb24.asanzeman.request.domain.port.in;

/**
 * The branch's work on an issuance request (4.1, SRS review use case). Every method runs in one short transaction under
 * {@code FOR UPDATE NOWAIT}, checks the branch rule, the request's status and the ownership, and is audited.
 *
 * <p>Common refusals: 404 {@code RequestNotFoundException}; 403 {@code NotYourBranchException}; 409
 * {@code RequestBusyException} (another call holds the row this instant), {@code RequestLockedByOtherException}
 * (someone else owns it, with their name) and {@code RequestNotReviewableException} (the step does not fit the status).
 */
public interface BranchReviewUseCase {

    /** «اقدام»: takes a request from the queue (or comes back to one the user already holds). */
    ReviewResult take(BranchCommand command);

    /** «تایید درخواست صدور»: the holder goes on to the digital signature and Farangin. */
    ReviewResult approve(BranchCommand command);

    /**
     * «رد درخواست صدور»: the holder rejects, with a reason from the admin's list and a comment of at most 200
     * characters. The customer gets an SMS.
     *
     * @throws com.sb24.asanzeman.request.domain.model.InvalidRequestDataException unknown reason, or no comment, or too long
     */
    ReviewResult reject(BranchCommand command, java.util.UUID reasonId, String comment);

    /**
     * The holder records the guarantee number from Farangin: «صادر شده». The customer gets an SMS.
     *
     * @throws com.sb24.asanzeman.request.domain.model.InvalidRequestDataException the number does not look right
     */
    ReviewResult issue(BranchCommand command, String guaranteeNumber);

    /** «انصراف از درخواست» (in review) or «لغو اقدام» (in action): back to the queue. */
    ReviewResult abandon(BranchCommand command);

    /**
     * «تایید درخواست ابطال»: the holder revoked the guarantee in Farangin and confirms it. The revocation request is
     * completed and the issuance request it belongs to is revoked, in the same transaction; the customer gets an SMS.
     */
    ReviewResult completeRevocation(BranchCommand command);

    /** An admin frees a request whoever holds it; the reason is required and audited. */
    ReviewResult forceRelease(BranchCommand command, String reason);

    /** The scheduler's call: frees the reviews nobody has touched for the idle time. @return how many were freed */
    int releaseIdle();
}
