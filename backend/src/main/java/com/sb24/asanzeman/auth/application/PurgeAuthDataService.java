package com.sb24.asanzeman.auth.application;

import com.sb24.asanzeman.auth.domain.port.in.PurgeAuthDataUseCase;
import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.auth.domain.port.out.OtpChallengeStorePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes what the login tables no longer need: blacklist entries of tokens that expired more than five minutes ago (an
 * expired token is refused anyway; the margin covers clock skew), and one-time-code records older than a day (a code is valid for two minutes; the day is kept for
 * support questions).
 */
@Service
@Transactional
public class PurgeAuthDataService implements PurgeAuthDataUseCase {

    static final Duration OTP_RETENTION = Duration.ofDays(1);
    /** A token is still accepted for a little after it expires (clock skew), so its entry is kept that much longer. */
    static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);

    private final JwtBlacklistPort blacklist;
    private final OtpChallengeStorePort otp;
    private final Clock clock;

    public PurgeAuthDataService(JwtBlacklistPort blacklist, OtpChallengeStorePort otp, Clock clock) {
        this.blacklist = blacklist;
        this.otp = otp;
        this.clock = clock;
    }

    @Override
    public int purge() {
        Instant now = clock.instant();
        return blacklist.deleteExpired(now.minus(TOKEN_MARGIN)) + otp.deleteSentBefore(now.minus(OTP_RETENTION));
    }
}
