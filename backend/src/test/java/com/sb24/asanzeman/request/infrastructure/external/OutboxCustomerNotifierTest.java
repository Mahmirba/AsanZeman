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
class OutboxCustomerNotifierTest {

    @Mock
    private EnqueueSmsUseCase sms;

    @Test
    void queuesTheRegistrationTextForTheCustomerWithTheAgencyName() {
        UUID request = UUID.randomUUID();

        new OutboxCustomerNotifier(sms).customerRegistered(request, "09121234567", "Sky Travel");

        verify(sms).enqueue(new EnqueueSmsCommand("09121234567", SmsTemplate.REQUEST_REGISTERED,
                List.of("Sky Travel"), request));
    }

    @Test
    void queuesTheRejectionAndTheIssueTextsWithTheAgencyTheBranchAndTheDate() {
        UUID request = UUID.randomUUID();
        var notifier = new OutboxCustomerNotifier(sms);

        notifier.requestRejected(request, "09121234567", "Sky Travel", "Valiasr", "1405/09/02");
        notifier.guaranteeIssued(request, "09121234567", "Sky Travel", "Valiasr", "1405/09/02");

        verify(sms).enqueue(new EnqueueSmsCommand("09121234567", SmsTemplate.REQUEST_REJECTED,
                List.of("Sky Travel", "Valiasr", "1405/09/02"), request));
        verify(sms).enqueue(new EnqueueSmsCommand("09121234567", SmsTemplate.GUARANTEE_ISSUED,
                List.of("Sky Travel", "Valiasr", "1405/09/02"), request));
    }

    @Test
    void queuesTheRevocationTextsWithTheAgencyTheBranchAndTheDate() {
        UUID request = UUID.randomUUID();
        var notifier = new OutboxCustomerNotifier(sms);

        notifier.guaranteeRevoked(request, "09121234567", "Sky Travel", "Valiasr", "1405/09/02");
        notifier.revocationRejected(request, "09121234567", "Sky Travel", "Valiasr", "1405/09/02");

        verify(sms).enqueue(new EnqueueSmsCommand("09121234567", SmsTemplate.GUARANTEE_REVOKED,
                List.of("Sky Travel", "Valiasr", "1405/09/02"), request));
        verify(sms).enqueue(new EnqueueSmsCommand("09121234567", SmsTemplate.REVOCATION_REJECTED,
                List.of("Sky Travel", "Valiasr", "1405/09/02"), request));
    }

    @Test
    void queuesTheCancellationTextWithTheAgencyNameAndTheJalaliDate() {
        UUID request = UUID.randomUUID();

        new OutboxCustomerNotifier(sms).requestCancelled(request, "09121234567", "Sky Travel", "1405/08/30");

        verify(sms).enqueue(new EnqueueSmsCommand("09121234567", SmsTemplate.REQUEST_CANCELLED,
                List.of("Sky Travel", "1405/08/30"), request));
    }
}
