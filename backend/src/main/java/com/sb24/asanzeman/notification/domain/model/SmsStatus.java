package com.sb24.asanzeman.notification.domain.model;

/** Where an outbox message stands: waiting (or waiting to retry), sent, or given up on (dead-letter, 6.4). */
public enum SmsStatus {
    PENDING,
    SENT,
    DEAD
}
