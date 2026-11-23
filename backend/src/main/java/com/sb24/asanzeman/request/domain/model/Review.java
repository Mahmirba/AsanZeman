package com.sb24.asanzeman.request.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A request as the branch works on it (4.1, 4.4): the request, who holds it (or {@code null}), and the branch that
 * handles it now, which is its own branch or the one that replaced it. Every step checks, in this order: the user's
 * branch may act on the request; then the request is where the step needs it; then the user owns it. All the steps
 * return a new {@code Review}; nothing here is saved.
 */
public record Review(Request request, ReviewLock lock, String handlingBranch) {

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
            return new Review(request, lock.touched(now), handlingBranch);
        }
        return new Review(request.reviewStarted(), new ReviewLock(user.id(), user.name(), now, now), handlingBranch);
    }

    /** «تایید درخواست صدور»: the holder approves; the request stays theirs through the signing and Farangin. */
    public Review approve(BranchUser user, Instant now) {
        requireHolder(user);
        return new Review(request.approvedForAction(), lock.touched(now), handlingBranch);
    }

    /** «رد درخواست صدور»: the holder rejects; the lock is cleared. */
    public Review reject(BranchUser user) {
        requireHolder(user);
        return new Review(request.rejectedByBranch(), null, handlingBranch);
    }

    /** The holder entered the guarantee number: «صادر شده»; the lock is cleared. */
    public Review issue(BranchUser user) {
        requireHolder(user);
        return new Review(request.issued(), null, handlingBranch);
    }

    /** «انصراف از درخواست» (in review) or «لغو اقدام» (in action): back to the queue, the lock cleared. */
    public Review abandon(BranchUser user) {
        requireHolder(user);
        return new Review(request.releasedToBranch(), null, handlingBranch);
    }

    /** An admin takes the request out of anybody's hands: back to the queue, whoever held it and in whatever state. */
    public Review forceRelease() {
        if (lock == null) {
            throw new RequestNotReviewableException("nobody holds the request");
        }
        return new Review(request.releasedToBranch(), null, handlingBranch);
    }

    /**
     * A request in review that its holder has left alone for {@code idle} goes back to the queue. A request in action
     * never does: the user may be half-way through Farangin (4.1).
     */
    public Optional<Review> autoReleased(Instant now, Duration idle) {
        boolean abandoned = lock != null && request.status() == RequestStatus.UNDER_REVIEW
                && !lock.lastActivityAt().plus(idle).isAfter(now);
        return abandoned ? Optional.of(new Review(request.releasedToBranch(), null, handlingBranch))
                : Optional.empty();
    }

    private void requireBranch(BranchUser user) {
        if (!canAct(user)) {
            throw new NotYourBranchException();
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
