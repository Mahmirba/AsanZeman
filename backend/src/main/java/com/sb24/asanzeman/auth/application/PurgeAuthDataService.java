package com.sb24.asanzeman.auth.application;

import com.sb24.asanzeman.auth.domain.port.in.PurgeAuthDataUseCase;
import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.auth.domain.port.out.OtpChallengeStorePort;
import com.sb24.asanzeman.auth.domain.port.out.RefreshTokenStorePort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes what the login tables no longer need:
 * <ul>
 *   <li>blacklist entries of tokens that expired more than five minutes ago (an expired token is refused anyway; the
 *       margin covers clock skew);</li>
 *   <li>one-time-code records, refresh tokens and Yaghut sessions that ended more than a day ago (a code is valid for
 *       two minutes and a session for 8 hours; the day is kept for support questions).</li>
 * </ul>
 */
@Service
@Transactional
public class PurgeAuthDataService implements PurgeAuthDataUseCase {

    static final Duration RETENTION = Duration.ofDays(1);
    /** A token is still accepted for a little after it expires (clock skew), so its entry is kept that much longer. */
    static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);

    private final JwtBlacklistPort blacklist;
    private final OtpChallengeStorePort otp;
    private final RefreshTokenStorePort refreshTokens;
    private final YaghutSessionStorePort yaghutSessions;
    private final Clock clock;

    public PurgeAuthDataService(JwtBlacklistPort blacklist, OtpChallengeStorePort otp,
            RefreshTokenStorePort refreshTokens, YaghutSessionStorePort yaghutSessions, Clock clock) {
        this.blacklist = blacklist;
        this.otp = otp;
        this.refreshTokens = refreshTokens;
        this.yaghutSessions = yaghutSessions;
        this.clock = clock;
    }

    @Override
    public int purge() {
        Instant now = clock.instant();
        Instant old = now.minus(RETENTION);
        return blacklist.deleteExpired(now.minus(TOKEN_MARGIN)) + otp.deleteSentBefore(old)
                + refreshTokens.deleteExpiredBefore(old) + yaghutSessions.deleteExpiredBefore(old);
    }
}
