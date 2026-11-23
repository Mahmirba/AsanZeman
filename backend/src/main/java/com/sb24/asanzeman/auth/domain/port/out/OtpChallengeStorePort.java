package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.OtpChallenge;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The codes sent and the wrong-code count, which is kept per customer, not per code (D34). */
public interface OtpChallengeStorePort {

    void save(OtpChallenge challenge);

    Optional<OtpChallenge> find(UUID id);

    /** Marks the code used. @return {@code false} when it was not waiting any more (used by another request) */
    boolean markVerified(UUID id);

    boolean isLocked(String nationalCode, Instant now);

    /**
     * Counts one wrong code; at {@code maxAttempts} the customer is locked until {@code now + lockout} and the count
     * starts again.
     *
     * @return {@code true} when this wrong code started the lockout
     */
    boolean recordWrong(String nationalCode, Instant now, int maxAttempts, Duration lockout);

    /** Deletes the codes sent before {@code before}. @return how many */
    int deleteSentBefore(Instant before);

    /** A right code ends the count. */
    void clearWrong(String nationalCode);
}
