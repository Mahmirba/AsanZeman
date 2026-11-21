package com.sb24.asanzeman.notification.domain.port.in;

/** Puts an SMS in the outbox, in the caller's transaction (6.4, D40): it is sent only if the caller commits. */
public interface EnqueueSmsUseCase {

    /**
     * @throws com.sb24.asanzeman.notification.domain.model.InvalidMobileException the number is not {@code 09xxxxxxxxx}
     */
    void enqueue(EnqueueSmsCommand command);
}
