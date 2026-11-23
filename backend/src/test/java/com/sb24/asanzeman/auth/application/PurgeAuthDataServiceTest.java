package com.sb24.asanzeman.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.auth.domain.port.out.OtpChallengeStorePort;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class PurgeAuthDataServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @Mock
    private JwtBlacklistPort blacklist;
    @Mock
    private OtpChallengeStorePort otp;

    @Test
    void deletesBlacklistEntriesExpiredFiveMinutesAgoAndCodesOlderThanADay() {
        when(blacklist.deleteExpired(NOW.minus(Duration.ofMinutes(5)))).thenReturn(4);
        when(otp.deleteSentBefore(NOW.minus(Duration.ofDays(1)))).thenReturn(3);

        int deleted = new PurgeAuthDataService(blacklist, otp, new MutableClock(NOW)).purge();

        assertThat(deleted).isEqualTo(7);
        verify(blacklist).deleteExpired(NOW.minus(Duration.ofMinutes(5)));
        verify(otp).deleteSentBefore(NOW.minus(Duration.ofDays(1)));
    }

    @Test
    void itIsOneTransaction() {
        assertThat(PurgeAuthDataService.class.getAnnotation(Transactional.class)).isNotNull();
    }
}
