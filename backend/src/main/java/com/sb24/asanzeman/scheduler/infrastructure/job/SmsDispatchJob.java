package com.sb24.asanzeman.scheduler.infrastructure.job;

import com.sb24.asanzeman.notification.domain.port.in.DispatchSmsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the SMS outbox (6.4): every few seconds ({@code azs.scheduler.sms-dispatch.fixed-delay}, default 5 s) after the last run
 * ends. ShedLock lets one instance send at a time. A failure is logged and never stops the schedule; the messages stay
 * in the outbox and are tried again.
 */
@Component
@ConditionalOnProperty(name = "azs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SmsDispatchJob {

    static final String LOCK = "sms-dispatch";
    private static final Logger log = LoggerFactory.getLogger(SmsDispatchJob.class);

    private final DispatchSmsUseCase dispatch;

    public SmsDispatchJob(DispatchSmsUseCase dispatch) {
        this.dispatch = dispatch;
    }

    @Scheduled(fixedDelayString = "${azs.scheduler.sms-dispatch.fixed-delay:5s}")
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    public void run() {
        try {
            int tried = dispatch.dispatchDue();
            if (tried > 0) {
                log.info("SMS outbox: tried {} message(s)", tried);
            }
        } catch (RuntimeException e) {
            log.error("SMS dispatch failed, the messages stay in the outbox", e);
        }
    }
}
