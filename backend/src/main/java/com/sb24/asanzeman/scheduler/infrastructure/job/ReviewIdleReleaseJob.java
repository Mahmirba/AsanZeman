package com.sb24.asanzeman.scheduler.infrastructure.job;

import com.sb24.asanzeman.request.domain.port.in.BranchReviewUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Frees the reviews nobody has touched (4.1): a request a branch user took with «اقدام» and then left idle for
 * {@code azs.lock.review-idle-timeout} (default 60 minutes) goes back to the queue. A request in action is never freed
 * here. It runs every few minutes ({@code azs.scheduler.lock-release.fixed-delay}); ShedLock lets one instance run it.
 * A failure is logged and never stops the schedule.
 */
@Component
@ConditionalOnProperty(name = "azs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReviewIdleReleaseJob {

    static final String LOCK = "review-idle-release";
    private static final Logger log = LoggerFactory.getLogger(ReviewIdleReleaseJob.class);

    private final BranchReviewUseCase reviews;

    public ReviewIdleReleaseJob(BranchReviewUseCase reviews) {
        this.reviews = reviews;
    }

    @Scheduled(fixedDelayString = "${azs.scheduler.lock-release.fixed-delay:5m}")
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void run() {
        try {
            int released = reviews.releaseIdle();
            if (released > 0) {
                log.info("freed {} idle review(s)", released);
            }
        } catch (RuntimeException e) {
            log.error("freeing the idle reviews failed, the next run tries again", e);
        }
    }
}
