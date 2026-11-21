package com.sb24.asanzeman.notification.application;

import com.sb24.asanzeman.notification.domain.model.OutboxSms;
import com.sb24.asanzeman.notification.domain.port.in.EnqueueSmsCommand;
import com.sb24.asanzeman.notification.domain.port.in.EnqueueSmsUseCase;
import com.sb24.asanzeman.notification.domain.port.out.SmsOutboxPort;
import com.sb24.asanzeman.notification.domain.port.out.SmsTextPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the message in the caller's transaction (6.4): the row commits with the status change that caused it, and
 * rolls back with it. The text is made now, so a later change to the template does not alter queued messages.
 */
@Service
public class SmsOutboxService implements EnqueueSmsUseCase {

    private final SmsTextPort texts;
    private final SmsOutboxPort outbox;
    private final Clock clock;

    public SmsOutboxService(SmsTextPort texts, SmsOutboxPort outbox, Clock clock) {
        this.texts = texts;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(EnqueueSmsCommand command) {
        String text = texts.render(command.template(), command.args());
        OutboxSms sms = new OutboxSms(UUID.randomUUID(), command.mobile(), command.template().name(), text,
                command.requestId(), 0);
        outbox.insert(sms, clock.instant());
    }
}
