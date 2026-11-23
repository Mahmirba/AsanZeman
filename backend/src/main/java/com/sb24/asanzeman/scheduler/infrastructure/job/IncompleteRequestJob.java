package com.sb24.asanzeman.scheduler.infrastructure.job;

import com.sb24.asanzeman.request.domain.port.in.ExpireIncompleteRequestsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The 7-day rule (4.2): rejects the requests the customer has not completed in time. It runs every hour
 * ({@code azs.scheduler.incomplete-requests.cron}); ShedLock lets one instance run it. A failure is logged and never
 * stops the schedule.
 */
@Component
@ConditionalOnProperty(name = "azs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class IncompleteRequestJob {

    static final String LOCK = "incomplete-requests";
    private static final Logger log = LoggerFactory.getLogger(IncompleteRequestJob.class);

    private final ExpireIncompleteRequestsUseCase expire;

    public IncompleteRequestJob(ExpireIncompleteRequestsUseCase expire) {
        this.expire = expire;
    }

    @Scheduled(cron = "${azs.scheduler.incomplete-requests.cron:0 5 * * * *}")
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT30M", lockAtLeastFor = "PT30S")
    public void run() {
        try {
            int rejected = expire.expireIncomplete();
            if (rejected > 0) {
                log.info("rejected {} request(s) not completed in time", rejected);
            }
        } catch (RuntimeException e) {
            log.error("rejecting the incomplete requests failed, the next run tries again", e);
        }
    }
}
