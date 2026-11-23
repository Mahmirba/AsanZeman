package com.sb24.asanzeman.scheduler.infrastructure.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.port.in.BranchReviewUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class ReviewIdleReleaseJobTest {

    @Mock
    private BranchReviewUseCase reviews;

    @Test
    void eachRunFreesTheIdleReviews() {
        when(reviews.releaseIdle()).thenReturn(2);

        new ReviewIdleReleaseJob(reviews).run();

        verify(reviews).releaseIdle();
    }

    @Test
    void aRunThatFindsNothingIsFine() {
        when(reviews.releaseIdle()).thenReturn(0);

        assertThatCode(() -> new ReviewIdleReleaseJob(reviews).run()).doesNotThrowAnyException();
    }

    @Test
    void aFailureIsLoggedAndNeverEscapesTheJob() {
        when(reviews.releaseIdle()).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> new ReviewIdleReleaseJob(reviews).run()).doesNotThrowAnyException();
    }

    @Test
    void itIsLeaderElectedAndRunsAfterTheLastRunEnds() throws Exception {
        var run = ReviewIdleReleaseJob.class.getMethod("run");

        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("review-idle-release");
        assertThat(run.getAnnotation(Scheduled.class).fixedDelayString()).contains("lock-release.fixed-delay");
    }
}
