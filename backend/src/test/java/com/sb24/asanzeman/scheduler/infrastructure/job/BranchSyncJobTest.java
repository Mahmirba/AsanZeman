package com.sb24.asanzeman.scheduler.infrastructure.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.port.in.SyncBranchesUseCase;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class BranchSyncJobTest {

    @Mock
    private SyncBranchesUseCase sync;

    @Test
    void theNightlyRunSyncs() {
        new BranchSyncJob(sync).nightly();

        verify(sync).sync();
    }

    @Test
    void aFailureIsLoggedAndNeverEscapesTheJob() {
        when(sync.sync()).thenThrow(new TemporarilyUnavailableException("Yaghut", new RuntimeException("down")));

        assertThatCode(() -> new BranchSyncJob(sync).nightly()).doesNotThrowAnyException();
    }

    @Test
    void startupSyncsWhenThereAreNoBranches() {
        when(sync.hasBranches()).thenReturn(false);

        new BranchSyncJob(sync).atStartup();

        verify(sync).sync();
    }

    @Test
    void startupDoesNothingWhenBranchesExist() {
        when(sync.hasBranches()).thenReturn(true);

        new BranchSyncJob(sync).atStartup();

        verify(sync, never()).sync();
    }

    @Test
    void aStartupFailureDoesNotStopTheApplication() {
        when(sync.hasBranches()).thenReturn(false);
        when(sync.sync()).thenThrow(new TemporarilyUnavailableException("Yaghut", new RuntimeException("down")));

        assertThatCode(() -> new BranchSyncJob(sync).atStartup()).doesNotThrowAnyException();
    }

    @Test
    void bothEntryPointsShareOneLockAndTheNightlyRunHasASchedule() throws Exception {
        var nightly = BranchSyncJob.class.getMethod("nightly");
        var startup = BranchSyncJob.class.getMethod("atStartup");

        assertThat(nightly.getAnnotation(SchedulerLock.class).name())
                .isEqualTo(startup.getAnnotation(SchedulerLock.class).name()).isEqualTo("branch-sync");
        assertThat(nightly.getAnnotation(Scheduled.class).cron()).contains("0 0 2 * * *");
    }
}
