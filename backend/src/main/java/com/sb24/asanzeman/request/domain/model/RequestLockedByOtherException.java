package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Another user took the request with «اقدام» and still holds it (4.1): 409, naming the holder and when they took it, as
 * a Jalali date and Tehran time. Any action on it by someone else is refused until the holder finishes, abandons it, or
 * an admin releases it.
 */
public class RequestLockedByOtherException extends DomainException {

    public static final String CODE = "request.locked-by-other";
        public RequestLockedByOtherException(ReviewLock lock) {
        super(CODE, Kind.CONFLICT, lock.holderName(), when(lock.lockedAt()));
    }

    private static String when(Instant instant) {
        LocalDateTime local = instant.atZone(JalaliDate.TEHRAN).toLocalDateTime();
        return JalaliDate.fromGregorian(local.toLocalDate()).toSlashed()
                + String.format(" %02d:%02d", local.getHour(), local.getMinute());
    }
}
