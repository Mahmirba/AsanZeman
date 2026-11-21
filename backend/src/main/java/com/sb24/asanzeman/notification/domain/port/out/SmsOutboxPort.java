package com.sb24.asanzeman.notification.domain.port.out;

import com.sb24.asanzeman.notification.domain.model.OutboxSms;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The {@code outbox_message} table. */
public interface SmsOutboxPort {

    /** Adds a pending message, due at once; joins the caller's transaction. */
    void insert(OutboxSms sms, Instant now);

    /**
     * Takes up to {@code limit} pending messages that are due, counts one more attempt on each, and hides them for
     * {@code lease} (their next attempt moves forward), so a worker that dies mid-send leaves them to be tried
     * again later. Rows another worker holds are skipped. Runs in its own short transaction.
     */
    List<OutboxSms> claimDue(Instant now, int limit, Duration lease);

    void markSent(UUID id, String smsId, Instant at);

    void markRetry(UUID id, Instant nextAttempt, String error);

    void markDead(UUID id, String error, Instant at);
}
