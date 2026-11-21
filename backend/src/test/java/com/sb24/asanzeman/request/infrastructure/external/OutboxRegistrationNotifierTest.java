package com.sb24.asanzeman.request.infrastructure.external;

import static org.mockito.Mockito.verify;

import com.sb24.asanzeman.notification.domain.port.in.EnqueueSmsCommand;
import com.sb24.asanzeman.notification.domain.port.in.EnqueueSmsUseCase;
import com.sb24.asanzeman.notification.domain.port.in.SmsTemplate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRegistrationNotifierTest {

    @Mock
    private EnqueueSmsUseCase sms;

    @Test
    void queuesTheRegistrationTextForTheCustomerWithTheAgencyName() {
        UUID request = UUID.randomUUID();

        new OutboxRegistrationNotifier(sms).customerRegistered(request, "09121234567", "Sky Travel");

        verify(sms).enqueue(new EnqueueSmsCommand("09121234567", SmsTemplate.REQUEST_REGISTERED,
                List.of("Sky Travel"), request));
    }
}
