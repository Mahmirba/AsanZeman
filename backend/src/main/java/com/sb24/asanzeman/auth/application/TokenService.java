package com.sb24.asanzeman.auth.application;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.InvalidSessionException;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.model.RefreshTokenRecord;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.port.in.LogoutUseCase;
import com.sb24.asanzeman.auth.domain.port.in.RefreshSessionUseCase;
import com.sb24.asanzeman.auth.domain.port.in.StartSessionUseCase;
import com.sb24.asanzeman.auth.domain.port.out.AccessTokenIssuerPort;
import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.auth.domain.port.out.RefreshTokenStorePort;
import com.sb24.asanzeman.auth.domain.service.RefreshTokens;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.DomainException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessions of every role (6, D5, D7): access token (RS256 JWT, 15 minutes) plus an opaque refresh token.
 *
 * <ul>
 *   <li><b>Rotation:</b> every refresh returns a new pair and uses up the old refresh token. The session's end, fixed
 *       at login, does not move.</li>
 *   <li><b>Reuse detection:</b> presenting a used token again revokes the whole family and writes a security audit
 *       event. That revoke must survive the 401, so domain exceptions do not roll the transaction back.</li>
 *   <li><b>Logout:</b> the access token is blacklisted until it expires and the family is revoked.</li>
 * </ul>
 */
@Service
@Transactional(noRollbackFor = DomainException.class)
public class TokenService implements StartSessionUseCase, RefreshSessionUseCase, LogoutUseCase {

    static final String ENTITY = "SESSION";

    private final AccessTokenIssuerPort issuer;
    private final RefreshTokenStorePort store;
    private final JwtBlacklistPort blacklist;
    private final RecordAuditUseCase audit;
    private final Clock clock;
    private final TokenSettings settings;

    public TokenService(AccessTokenIssuerPort issuer, RefreshTokenStorePort store, JwtBlacklistPort blacklist,
            RecordAuditUseCase audit, Clock clock, TokenSettings settings) {
        this.issuer = issuer;
        this.store = store;
        this.blacklist = blacklist;
        this.audit = audit;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public IssuedTokens start(Session session, ClientInfo client) {
        return start(session, UUID.randomUUID(), null, client);
    }

    @Override
    public IssuedTokens start(Session session, UUID family, Instant latestEnd, ClientInfo client) {
        Instant now = clock.instant();
        Instant end = now.plus(settings.refreshTtl());
        if (latestEnd != null && latestEnd.isBefore(end)) {
            end = latestEnd;
        }
        IssuedTokens tokens = session.role() == Role.CUSTOMER ? issueSingle(session, family, now, end)
                : issue(session, family, now, end);
        audit.record(entry(session, family, "LOGIN", null, client));
        return tokens;
    }

    /** D7: a customer gets one session and no refresh token, so its access token lasts the whole session. */
    private IssuedTokens issueSingle(Session session, UUID family, Instant now, Instant sessionEnd) {
        String accessToken = issuer.issue(session, family, UUID.randomUUID().toString(), now, sessionEnd);
        return new IssuedTokens(accessToken, sessionEnd, null, sessionEnd);
    }

    @Override
    public IssuedTokens refresh(String refreshToken, ClientInfo client) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidSessionException();
        }
        Instant now = clock.instant();
        RefreshTokenRecord stored = store.findByHash(RefreshTokens.hash(refreshToken.trim()))
                .orElseThrow(InvalidSessionException::new);
        if (stored.revoked() || stored.expired(now)) {
            throw new InvalidSessionException();
        }
        if (stored.used() || !store.markUsed(stored.id(), now)) {
            // a used token is presented again: someone holds a copy, so the whole family goes
            store.revokeFamily(stored.familyId(), now);
            audit.record(entry(stored.session(), stored.familyId(), "REFRESH_REUSE",
                    "refresh token used twice, session revoked", client));
            throw new InvalidSessionException();
        }
        return issue(stored.session(), stored.familyId(), now, stored.expiresAt());
    }

    @Override
    public void logout(String jti, Instant accessTokenExpiresAt, String subject, UUID familyId,
            ClientInfo client) {
        Instant now = clock.instant();
        blacklist.revoke(jti, accessTokenExpiresAt, "LOGOUT");
        if (familyId != null) {
            store.revokeFamily(familyId, now);
        }
        audit.record(new AuditEntry(null, subject, null, ENTITY, null, "LOGOUT", null, null, null, client.ip(),
                client.userAgent(), null));
    }

    private IssuedTokens issue(Session session, UUID family, Instant now, Instant sessionEnd) {
        Instant accessExpiresAt = now.plus(settings.accessTtl());
        if (accessExpiresAt.isAfter(sessionEnd)) {
            accessExpiresAt = sessionEnd; // an access token never outlives its session
        }
        String accessToken = issuer.issue(session, family, UUID.randomUUID().toString(), now, accessExpiresAt);
        String refreshToken = RefreshTokens.newToken();
        store.save(new RefreshTokenRecord(UUID.randomUUID(), family, session, RefreshTokens.hash(refreshToken),
                sessionEnd, null, null));
        return new IssuedTokens(accessToken, accessExpiresAt, refreshToken, sessionEnd);
    }

    private static AuditEntry entry(Session session, UUID family, String action, String reason, ClientInfo client) {
        return new AuditEntry(session.subjectId(), session.subjectName(), session.role().name(), ENTITY, family,
                action, null, null, reason, client.ip(), client.userAgent(), null);
    }
}
