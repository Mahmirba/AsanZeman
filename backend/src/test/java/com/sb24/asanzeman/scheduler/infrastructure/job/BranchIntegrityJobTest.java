package com.sb24.asanzeman.scheduler.infrastructure.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.port.in.CheckBranchIntegrityUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class BranchIntegrityJobTest {

    @Mock
    private CheckBranchIntegrityUseCase integrity;

    @Test
    void itChecksAtStartupAndEveryNight() {
        var job = new BranchIntegrityJob(integrity);

        job.atStartup();
        job.nightly();

        verify(integrity, times(2)).check();
    }

    @Test
    void aFailureIsLoggedAndNeverEscapesTheJob() {
        when(integrity.check()).thenThrow(new IllegalStateException("database down"));
        var job = new BranchIntegrityJob(integrity);

        assertThatCode(job::atStartup).doesNotThrowAnyException();
        assertThatCode(job::nightly).doesNotThrowAnyException();
    }

    @Test
    void itIsLeaderElectedAndScheduledAfterTheSync() throws Exception {
        var nightly = BranchIntegrityJob.class.getMethod("nightly");

        assertThat(nightly.getAnnotation(SchedulerLock.class).name()).isEqualTo("branch-integrity");
        assertThat(nightly.getAnnotation(Scheduled.class).cron()).contains("branch.integrity.cron");
        assertThat(BranchIntegrityJob.class.getMethod("atStartup").getAnnotation(SchedulerLock.class).name())
                .isEqualTo("branch-integrity");
    }
}
