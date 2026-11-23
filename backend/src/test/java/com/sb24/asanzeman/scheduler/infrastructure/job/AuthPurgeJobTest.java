package com.sb24.asanzeman.scheduler.infrastructure.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.port.in.PurgeAuthDataUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class AuthPurgeJobTest {

    @Mock
    private PurgeAuthDataUseCase purge;

    @Test
    void eachRunPurgesTheLoginTables() {
        when(purge.purge()).thenReturn(5);

        new AuthPurgeJob(purge).run();

        verify(purge).purge();
    }

    @Test
    void aRunThatDeletesNothingIsFine() {
        when(purge.purge()).thenReturn(0);

        assertThatCode(() -> new AuthPurgeJob(purge).run()).doesNotThrowAnyException();
    }

    @Test
    void aFailureIsLoggedAndNeverEscapesTheJob() {
        when(purge.purge()).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> new AuthPurgeJob(purge).run()).doesNotThrowAnyException();
    }

    @Test
    void itIsLeaderElectedAndRunsNightly() throws Exception {
        var run = AuthPurgeJob.class.getMethod("run");

        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("auth-purge");
        assertThat(run.getAnnotation(Scheduled.class).cron()).contains("auth-purge.cron");
    }
}
