package com.sb24.asanzeman.scheduler.infrastructure.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.port.in.ExpireIncompleteRequestsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class IncompleteRequestJobTest {

    @Mock
    private ExpireIncompleteRequestsUseCase expire;

    @Test
    void eachRunRejectsTheOverdueRequests() {
        when(expire.expireIncomplete()).thenReturn(3);

        new IncompleteRequestJob(expire).run();

        verify(expire).expireIncomplete();
    }

    @Test
    void aRunThatFindsNothingIsFine() {
        when(expire.expireIncomplete()).thenReturn(0);

        assertThatCode(() -> new IncompleteRequestJob(expire).run()).doesNotThrowAnyException();
    }

    @Test
    void aFailureIsLoggedAndNeverEscapesTheJob() {
        when(expire.expireIncomplete()).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> new IncompleteRequestJob(expire).run()).doesNotThrowAnyException();
    }

    @Test
    void itIsLeaderElectedAndRunsHourly() throws Exception {
        var run = IncompleteRequestJob.class.getMethod("run");

        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("incomplete-requests");
        assertThat(run.getAnnotation(Scheduled.class).cron()).contains("incomplete-requests.cron");
    }
}
