package com.sb24.asanzeman.request.infrastructure.external;

import com.sb24.asanzeman.notification.domain.port.in.EnqueueSmsCommand;
import com.sb24.asanzeman.notification.domain.port.in.EnqueueSmsUseCase;
import com.sb24.asanzeman.notification.domain.port.in.SmsTemplate;
import com.sb24.asanzeman.request.domain.port.out.RegistrationNotifierPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Event 1 of the SMS table (6.4): the customer is told to complete the request. The message goes into the outbox in
 * the registration's own transaction, so it is sent only if the registration commits.
 */
@Component
public class OutboxRegistrationNotifier implements RegistrationNotifierPort {

    private final EnqueueSmsUseCase sms;

    public OutboxRegistrationNotifier(EnqueueSmsUseCase sms) {
        this.sms = sms;
    }

    @Override
    public void customerRegistered(UUID requestId, String mobile, String agencyName) {
        sms.enqueue(new EnqueueSmsCommand(mobile, SmsTemplate.REQUEST_REGISTERED, List.of(agencyName), requestId));
    }
}
