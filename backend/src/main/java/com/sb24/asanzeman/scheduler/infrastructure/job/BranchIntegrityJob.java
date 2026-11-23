package com.sb24.asanzeman.scheduler.infrastructure.job;

import com.sb24.asanzeman.request.domain.port.in.CheckBranchIntegrityUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 4.4: checks the branch table at startup and every night at 02:30, after the sync ({@code azs.branch.integrity.cron}).
 * The problems are raised to ops by the check itself; this job only makes sure a failure of the check never stops the
 * application or the schedule.
 */
@Component
@ConditionalOnProperty(name = "azs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BranchIntegrityJob {

    static final String LOCK = "branch-integrity";
    private static final Logger log = LoggerFactory.getLogger(BranchIntegrityJob.class);

    private final CheckBranchIntegrityUseCase integrity;

    public BranchIntegrityJob(CheckBranchIntegrityUseCase integrity) {
        this.integrity = integrity;
    }

    @Scheduled(cron = "${azs.branch.integrity.cron:0 30 2 * * *}")
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void nightly() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void atStartup() {
        run();
    }

    private void run() {
        try {
            integrity.check();
        } catch (RuntimeException e) {
            log.error("the branch integrity check failed, the next run tries again", e);
        }
    }
}
