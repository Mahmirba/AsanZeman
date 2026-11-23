package com.sb24.asanzeman.request.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A request as the branch works on it (4.1, 4.2, 4.4): the request, who holds it (or {@code null}), the branch that
 * handles it now (its own branch or the one that replaced it), the issuance request a revocation belongs to
 * ({@code parentId}), and whether an issuance request has a revocation open against it ({@code revocationOpen}). Every
 * step checks, in this order: the user's branch may act on the request; then the request is where the step needs it; then
 * the user owns it. All the steps return a new {@code Review}; nothing here is saved.
 */
public record Review(Request request, ReviewLock lock, String handlingBranch, UUID parentId,
        boolean revocationOpen) {

    public Review(Request request, ReviewLock lock, String handlingBranch) {
        this(request, lock, handlingBranch, null, false);
    }

    /** The branch rule (4.4): the original branch, even if closed, or the branch that handles it now. */
    public boolean canAct(BranchUser user) {
        return user.branchCode().equals(request.branchCode()) || user.branchCode().equals(handlingBranch);
    }

    /**
     * «اقدام»: takes a request waiting in the queue. Taking one the user already holds is fine (they come back to it);
     * taking one someone else holds is refused with the holder's name.
     */
    public Review take(BranchUser user, Instant now) {
        requireBranch(user);
        if (lock != null) {
            if (!lock.heldBy(user)) {
                throw new RequestLockedByOtherException(lock);
            }
            return with(request, lock.touched(now));
        }
        return with(request.reviewStarted(), new ReviewLock(user.id(), user.name(), now, now));
    }

    /**
     * «تایید»: the holder approves; the request stays theirs through the signing and Farangin. While a revocation is open
     * against an issuance request, the issuance can no longer be approved (D17).
     */
    public Review approve(BranchUser user, Instant now) {
        requireHolder(user);
        requireNoRevocation();
        return with(request.approvedForAction(), lock.touched(now));
    }

    /** «رد»: the holder rejects; the lock is cleared. An issuance with a revocation open cannot be rejected (D17). */
    public Review reject(BranchUser user) {
        requireHolder(user);
        requireNoRevocation();
        return with(request.rejectedByBranch(), null);
    }

    /**
     * The holder entered the guarantee number: «صادر شده»; the lock is cleared. This stays possible while a revocation is
     * open: the holder may record a guarantee that was already issued in Farangin, so the records match reality (D17).
     */
    public Review issue(BranchUser user) {
        requireHolder(user);
        return with(request.issued(), null);
    }

    /** The holder revoked the guarantee in Farangin and confirms it: the revocation request is completed. */
    public Review completeRevocation(BranchUser user) {
        requireHolder(user);
        return with(request.revocationCompleted(), null);
    }

    /** This issuance request's revocation was completed: it is revoked, and nobody holds it any more (4.2). */
    public Review revoked() {
        return with(request.revoked(), null);
    }

    /** «انصراف از درخواست» (in review) or «لغو اقدام» (in action): back to the queue, the lock cleared. */
    public Review abandon(BranchUser user) {
        requireHolder(user);
        return with(request.releasedToBranch(), null);
    }

    /** An admin takes the request out of anybody's hands: back to the queue, whoever held it and in whatever state. */
    public Review forceRelease() {
        if (lock == null) {
            throw new RequestNotReviewableException("nobody holds the request");
        }
        return with(request.releasedToBranch(), null);
    }

    /**
     * A request in review that its holder has left alone for {@code idle} goes back to the queue. A request in action
     * never does: the user may be half-way through Farangin (4.1).
     */
    public Optional<Review> autoReleased(Instant now, Duration idle) {
        boolean abandoned = lock != null && request.status() == RequestStatus.UNDER_REVIEW
                && !lock.lastActivityAt().plus(idle).isAfter(now);
        return abandoned ? Optional.of(with(request.releasedToBranch(), null)) : Optional.empty();
    }

    private Review with(Request newRequest, ReviewLock newLock) {
        return new Review(newRequest, newLock, handlingBranch, parentId, revocationOpen);
    }

    private void requireBranch(BranchUser user) {
        if (!canAct(user)) {
            throw new NotYourBranchException();
        }
    }

    private void requireNoRevocation() {
        if (revocationOpen) {
            throw new RequestNotReviewableException("a revocation is open against the request");
        }
    }

    private void requireHolder(BranchUser user) {
        requireBranch(user);
        if (lock == null) {
            throw new RequestNotReviewableException("the request was not taken with «اقدام»");
        }
        if (!lock.heldBy(user)) {
            throw new RequestLockedByOtherException(lock);
        }
    }
}
