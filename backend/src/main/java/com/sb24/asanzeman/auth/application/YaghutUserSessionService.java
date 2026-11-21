package com.sb24.asanzeman.auth.application;

import com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException;
import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import com.sb24.asanzeman.auth.domain.port.in.YaghutUserSessionUseCase;
import com.sb24.asanzeman.auth.domain.port.out.RefreshTokenStorePort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
import com.sb24.asanzeman.shared.model.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5.4: the Asan Zeman session of an agency or customer cannot outlast their Yaghut session. When the Yaghut session
 * is missing, past its end, or reported invalid by Yaghut, the Asan Zeman session is revoked too, so a refresh token
 * cannot bring it back. The exception is thrown after the clean-up, so domain exceptions must not roll it back.
 */
@Service
@Transactional(noRollbackFor = DomainException.class)
public class YaghutUserSessionService implements YaghutUserSessionUseCase {

    private final YaghutSessionStorePort store;
    private final RefreshTokenStorePort refreshTokens;
    private final Clock clock;

    public YaghutUserSessionService(YaghutSessionStorePort store, RefreshTokenStorePort refreshTokens, Clock clock) {
        this.store = store;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Override
    public YaghutUserSession require(UUID sessionId) {
        Instant now = clock.instant();
        YaghutUserSession session = store.find(sessionId).orElseThrow(YaghutSessionExpiredException::new);
        if (session.expired(now)) {
            end(sessionId);
            throw new YaghutSessionExpiredException();
        }
        return session;
    }

    @Override
    public void end(UUID sessionId) {
        store.delete(sessionId);
        refreshTokens.revokeFamily(sessionId, clock.instant());
    }
}
