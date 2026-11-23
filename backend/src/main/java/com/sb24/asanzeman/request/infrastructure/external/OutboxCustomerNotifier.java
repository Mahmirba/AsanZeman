package com.sb24.asanzeman.request.infrastructure.external;

import com.sb24.asanzeman.notification.domain.port.in.EnqueueSmsCommand;
import com.sb24.asanzeman.notification.domain.port.in.EnqueueSmsUseCase;
import com.sb24.asanzeman.notification.domain.port.in.SmsTemplate;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The customer's SMS about their request (6.4). Each message goes into the outbox in the caller's own transaction,
 * so it is sent only if the change that caused it commits.
 */
@Component
public class OutboxCustomerNotifier implements CustomerNotifierPort {

    private final EnqueueSmsUseCase sms;

    public OutboxCustomerNotifier(EnqueueSmsUseCase sms) {
        this.sms = sms;
    }

    @Override
    public void customerRegistered(UUID requestId, String mobile, String agencyName) {
        sms.enqueue(new EnqueueSmsCommand(mobile, SmsTemplate.REQUEST_REGISTERED, List.of(agencyName), requestId));
    }

    @Override
    public void requestRejected(UUID requestId, String mobile, String agencyName, String branchName,
            String sentDate) {
        sms.enqueue(new EnqueueSmsCommand(mobile, SmsTemplate.REQUEST_REJECTED,
                List.of(agencyName, branchName, sentDate), requestId));
    }

    @Override
    public void guaranteeIssued(UUID requestId, String mobile, String agencyName, String branchName,
            String sentDate) {
        sms.enqueue(new EnqueueSmsCommand(mobile, SmsTemplate.GUARANTEE_ISSUED,
                List.of(agencyName, branchName, sentDate), requestId));
    }

    @Override
    public void guaranteeRevoked(UUID requestId, String mobile, String agencyName, String branchName,
            String sentDate) {
        sms.enqueue(new EnqueueSmsCommand(mobile, SmsTemplate.GUARANTEE_REVOKED,
                List.of(agencyName, branchName, sentDate), requestId));
    }

    @Override
    public void revocationRejected(UUID requestId, String mobile, String agencyName, String branchName,
            String sentDate) {
        sms.enqueue(new EnqueueSmsCommand(mobile, SmsTemplate.REVOCATION_REJECTED,
                List.of(agencyName, branchName, sentDate), requestId));
    }

    @Override
    public void agencyRevocationCompleted(UUID requestId, String agencyMobile, String trackingCode,
            String branchName, String sentDate) {
        toAgency(SmsTemplate.AGENCY_REVOCATION_COMPLETED, requestId, agencyMobile, trackingCode, branchName, sentDate);
    }

    @Override
    public void agencyRevocationRejected(UUID requestId, String agencyMobile, String trackingCode,
            String branchName, String sentDate) {
        toAgency(SmsTemplate.AGENCY_REVOCATION_REJECTED, requestId, agencyMobile, trackingCode, branchName, sentDate);
    }

    /** An agency without a mobile number is simply not told. */
    private void toAgency(SmsTemplate template, UUID requestId, String mobile, String trackingCode, String branchName,
            String sentDate) {
        if (mobile != null && !mobile.isBlank()) {
            sms.enqueue(new EnqueueSmsCommand(mobile, template, List.of(trackingCode, branchName, sentDate),
                    requestId));
        }
    }

    @Override
    public void requestRejectedByInquiry(UUID requestId, String mobile, String agencyName, String sentDate) {
        sms.enqueue(new EnqueueSmsCommand(mobile, SmsTemplate.REQUEST_REJECTED_BY_INQUIRY, List.of(agencyName, sentDate),
                requestId));
    }

    @Override
    public void requestExpired(UUID requestId, String mobile, String agencyName, String sentDate) {
        sms.enqueue(new EnqueueSmsCommand(mobile, SmsTemplate.REQUEST_EXPIRED, List.of(agencyName, sentDate), requestId));
    }

    @Override
    public void requestCancelled(UUID requestId, String mobile, String agencyName, String sentDate) {
        sms.enqueue(new EnqueueSmsCommand(mobile, SmsTemplate.REQUEST_CANCELLED, List.of(agencyName, sentDate),
                requestId));
    }
}
