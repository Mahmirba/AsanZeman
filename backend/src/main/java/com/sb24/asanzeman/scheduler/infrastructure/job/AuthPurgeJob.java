package com.sb24.asanzeman.scheduler.infrastructure.job;

import com.sb24.asanzeman.auth.domain.port.in.PurgeAuthDataUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Housekeeping for the login tables, every night at 03:00 ({@code azs.scheduler.auth-purge.cron}): old one-time-code
 * records and blacklist entries of expired tokens. ShedLock lets one instance run it. A failure is logged and never
 * stops the schedule.
 */
@Component
@ConditionalOnProperty(name = "azs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AuthPurgeJob {

    static final String LOCK = "auth-purge";
    private static final Logger log = LoggerFactory.getLogger(AuthPurgeJob.class);

    private final PurgeAuthDataUseCase purge;

    public AuthPurgeJob(PurgeAuthDataUseCase purge) {
        this.purge = purge;
    }

    @Scheduled(cron = "${azs.scheduler.auth-purge.cron:0 0 3 * * *}")
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void run() {
        try {
            int deleted = purge.purge();
            if (deleted > 0) {
                log.info("deleted {} old login row(s)", deleted);
            }
        } catch (RuntimeException e) {
            log.error("purging the login tables failed, the next run tries again", e);
        }
    }
}
