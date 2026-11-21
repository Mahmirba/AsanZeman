package com.sb24.asanzeman.scheduler.infrastructure.job;

import com.sb24.asanzeman.request.domain.port.in.SyncBranchesUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 4.4: keeps the {@code branch} table in line with Yaghut. It runs once at startup when the table is empty (requests
 * cannot be registered without branches), then on a schedule (default 02:00 every night, {@code azs.branch.sync.cron}).
 * ShedLock lets one instance run it at a time. A failure never stops the application: it is logged for ops and the
 * next run tries again, and nothing is changed (Yaghut is read completely before anything is written).
 */
@Component
@ConditionalOnProperty(name = "azs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BranchSyncJob {

    static final String LOCK = "branch-sync";
    private static final Logger log = LoggerFactory.getLogger(BranchSyncJob.class);

    private final SyncBranchesUseCase sync;

    public BranchSyncJob(SyncBranchesUseCase sync) {
        this.sync = sync;
    }

    @Scheduled(cron = "${azs.branch.sync.cron:0 0 2 * * *}")
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void nightly() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void atStartup() {
        if (!sync.hasBranches()) {
            run();
        }
    }

    private void run() {
        try {
            sync.sync();
        } catch (RuntimeException e) {
            log.error("Branch sync failed, nothing changed; the next run tries again", e);
        }
    }
}
