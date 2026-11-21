package com.sb24.asanzeman.notification.application;

import java.time.Duration;

/**
 * Outbox tuning (6.4). {@code lease} is how long a claimed message stays hidden from other workers; {@code retryBase}
 * doubles with each attempt up to {@code retryMax}; {@code credentialsBackoff} is the long wait after a 403.
 */
public record SmsSettings(int batchSize, Duration lease, Duration retryBase, Duration retryMax,
        Duration credentialsBackoff) {

    public SmsSettings {
        if (batchSize < 1) {
            throw new IllegalArgumentException("the batch size must be at least 1");
        }
    }

    /** The wait after the {@code attempts}-th failed try: base, 2x base, 4x base, ... capped at {@code retryMax}. */
    public Duration retryDelay(int attempts) {
        long factor = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration delay = retryBase.multipliedBy(factor);
        return delay.compareTo(retryMax) > 0 ? retryMax : delay;
    }
}
