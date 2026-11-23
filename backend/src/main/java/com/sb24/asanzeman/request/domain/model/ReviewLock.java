package com.sb24.asanzeman.request.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Who owns a request that a branch user took with «اقدام» (4.1): the holder, their name for the inbox label, when they
 * took it, and when they last did anything with it. It lasts across HTTP calls, unlike the row lock, which lasts one.
 */
public record ReviewLock(UUID holderId, String holderName, Instant lockedAt, Instant lastActivityAt) {

    public ReviewLock touched(Instant now) {
        return new ReviewLock(holderId, holderName, lockedAt, now);
    }

    public boolean heldBy(BranchUser user) {
        return holderId.equals(user.id());
    }
}
