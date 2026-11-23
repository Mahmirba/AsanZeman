package com.sb24.asanzeman.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.auth.domain.port.out.OtpChallengeStorePort;
import com.sb24.asanzeman.auth.domain.port.out.RefreshTokenStorePort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
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
    private static final Instant A_DAY_AGO = NOW.minus(Duration.ofDays(1));

    @Mock
    private JwtBlacklistPort blacklist;
    @Mock
    private OtpChallengeStorePort otp;
    @Mock
    private RefreshTokenStorePort refreshTokens;
    @Mock
    private YaghutSessionStorePort yaghutSessions;

    @Test
    void deletesWhatHasEndedAndCountsIt() {
        when(blacklist.deleteExpired(NOW.minus(Duration.ofMinutes(5)))).thenReturn(4);
        when(otp.deleteSentBefore(A_DAY_AGO)).thenReturn(3);
        when(refreshTokens.deleteExpiredBefore(A_DAY_AGO)).thenReturn(2);
        when(yaghutSessions.deleteExpiredBefore(A_DAY_AGO)).thenReturn(1);

        int deleted = new PurgeAuthDataService(blacklist, otp, refreshTokens, yaghutSessions, new MutableClock(NOW))
                .purge();

        assertThat(deleted).isEqualTo(10);
        verify(blacklist).deleteExpired(NOW.minus(Duration.ofMinutes(5)));
        verify(otp).deleteSentBefore(A_DAY_AGO);
        verify(refreshTokens).deleteExpiredBefore(A_DAY_AGO);
        verify(yaghutSessions).deleteExpiredBefore(A_DAY_AGO);
    }

    @Test
    void itIsOneTransaction() {
        assertThat(PurgeAuthDataService.class.getAnnotation(Transactional.class)).isNotNull();
    }
}
