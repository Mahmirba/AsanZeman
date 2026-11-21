package com.sb24.asanzeman.scheduler.infrastructure.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.notification.domain.port.in.DispatchSmsUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class SmsDispatchJobTest {

    @Mock
    private DispatchSmsUseCase dispatch;

    @Test
    void eachRunDispatchesTheDueMessages() {
        when(dispatch.dispatchDue()).thenReturn(3);

        new SmsDispatchJob(dispatch).run();

        verify(dispatch).dispatchDue();
    }

    @Test
    void anEmptyRunIsFine() {
        when(dispatch.dispatchDue()).thenReturn(0);

        assertThatCode(() -> new SmsDispatchJob(dispatch).run()).doesNotThrowAnyException();
    }

    @Test
    void aFailureIsLoggedAndNeverEscapesTheJob() {
        when(dispatch.dispatchDue()).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> new SmsDispatchJob(dispatch).run()).doesNotThrowAnyException();
    }

    @Test
    void itIsLeaderElectedAndRunsAfterTheLastRunEnds() throws Exception {
        var run = SmsDispatchJob.class.getMethod("run");

        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("sms-dispatch");
        assertThat(run.getAnnotation(Scheduled.class).fixedDelayString()).contains("azs.sms.dispatch-delay");
    }
}
