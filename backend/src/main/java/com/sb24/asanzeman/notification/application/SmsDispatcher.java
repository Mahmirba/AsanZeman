package com.sb24.asanzeman.notification.application;

import com.sb24.asanzeman.notification.domain.model.OutboxSms;
import com.sb24.asanzeman.notification.domain.model.SmsCredentialsException;
import com.sb24.asanzeman.notification.domain.model.SmsRejectedException;
import com.sb24.asanzeman.notification.domain.port.in.DispatchSmsUseCase;
import com.sb24.asanzeman.notification.domain.port.out.SmsGatewayPort;
import com.sb24.asanzeman.notification.domain.port.out.SmsOutboxPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends the due messages (6.4). The claim is its own short transaction; the sends run outside any transaction, so a
 * slow SMS centre never holds a database lock. Each outcome is written right after its send:
 * <ul>
 *   <li>sent: {@code SENT} with the SMS centre's {@code smsId};</li>
 *   <li>400: {@code DEAD} (dead-letter) and an ERROR log, never retried;</li>
 *   <li>403: stays pending with a long backoff and an ERROR log, so it goes out once the credentials are fixed;</li>
 *   <li>anything else (500, timeout, network): retried with a growing backoff.</li>
 * </ul>
 * A failure on one message never stops the others.
 */
@Service
public class SmsDispatcher implements DispatchSmsUseCase {

    private static final Logger log = LoggerFactory.getLogger(SmsDispatcher.class);
    static final int MAX_ERROR = 500;

    private final SmsOutboxPort outbox;
    private final SmsGatewayPort gateway;
    private final Clock clock;
    private final SmsSettings settings;

    public SmsDispatcher(SmsOutboxPort outbox, SmsGatewayPort gateway, Clock clock, SmsSettings settings) {
        this.outbox = outbox;
        this.gateway = gateway;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public int dispatchDue() {
        List<OutboxSms> due = outbox.claimDue(clock.instant(), settings.batchSize(), settings.lease());
        for (OutboxSms sms : due) {
            send(sms);
        }
        return due.size();
    }

    private void send(OutboxSms sms) {
        try {
            String smsId = gateway.send(sms.gatewayNumber(), sms.text());
            outbox.markSent(sms.id(), smsId, clock.instant());
        } catch (SmsRejectedException e) {
            log.error("SMS {} for request {} refused by the SMS centre, dead-lettered: {}", sms.id(), sms.requestId(),
                    e.getMessage());
            outbox.markDead(sms.id(), cut(e.getMessage()), clock.instant());
        } catch (SmsCredentialsException e) {
            log.error("SMS centre refuses our credentials, SMS {} kept for later: {}", sms.id(), e.getMessage());
            outbox.markRetry(sms.id(), later(settings.credentialsBackoff()), cut(e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("SMS {} not sent (attempt {}), will retry: {}", sms.id(), sms.attempts(), e.toString());
            outbox.markRetry(sms.id(), later(settings.retryDelay(sms.attempts())), cut(e.toString()));
        }
    }

    private Instant later(java.time.Duration delay) {
        return clock.instant().plus(delay);
    }

    private static String cut(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_ERROR ? text : text.substring(0, MAX_ERROR);
    }
}
