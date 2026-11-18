package com.sb24.asanzeman.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.InvalidSessionException;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.model.RefreshTokenRecord;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.port.out.AccessTokenIssuerPort;
import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.auth.domain.port.out.RefreshTokenStorePort;
import com.sb24.asanzeman.auth.domain.service.RefreshTokens;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Duration ACCESS = Duration.ofMinutes(15);
    private static final Duration REFRESH = Duration.ofHours(8);
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.5", "Firefox");
    private static final Session SESSION = new Session(UUID.randomUUID(), "ali", Role.BRANCH, "0123");

    @Mock
    private AccessTokenIssuerPort issuer;
    @Mock
    private RefreshTokenStorePort store;
    @Mock
    private JwtBlacklistPort blacklist;
    @Mock
    private RecordAuditUseCase audit;

    private MutableClock clock;
    private TokenService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new TokenService(issuer, store, blacklist, audit, clock, new TokenSettings(ACCESS, REFRESH));
    }

    private RefreshTokenRecord stored(String token, Instant usedAt, Instant revokedAt, Instant expiresAt) {
        return new RefreshTokenRecord(UUID.randomUUID(), UUID.randomUUID(), SESSION, RefreshTokens.hash(token),
                expiresAt, usedAt, revokedAt);
    }

    // ---- start -------------------------------------------------------------------------------------------------

    @Test
    void startIssuesTheTokensStoresTheHashAndAuditsTheLogin() {
        when(issuer.issue(eq(SESSION), anyString(), eq(NOW), eq(NOW.plus(ACCESS)))).thenReturn("signed-jwt");

        IssuedTokens tokens = service.start(SESSION, CLIENT);

        assertThat(tokens.accessToken()).isEqualTo("signed-jwt");
        assertThat(tokens.accessTokenExpiresAt()).isEqualTo(NOW.plus(ACCESS));
        assertThat(tokens.refreshTokenExpiresAt()).isEqualTo(NOW.plus(REFRESH));
        ArgumentCaptor<RefreshTokenRecord> saved = ArgumentCaptor.forClass(RefreshTokenRecord.class);
        verify(store).save(saved.capture());
        // only the hash is stored, never the token itself
        assertThat(saved.getValue().tokenHash()).isEqualTo(RefreshTokens.hash(tokens.refreshToken()))
                .isNotEqualTo(tokens.refreshToken());
        assertThat(saved.getValue().session()).isEqualTo(SESSION);
        assertThat(saved.getValue().expiresAt()).isEqualTo(NOW.plus(REFRESH));
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::userName, AuditEntry::userRole,
                AuditEntry::entityId, AuditEntry::ipAddress, AuditEntry::userAgent)
                .containsExactly("LOGIN", "ali", "BRANCH", saved.getValue().familyId(), "10.0.0.5", "Firefox");
    }

    @Test
    void everyAccessTokenGetsItsOwnId() {
        when(issuer.issue(any(), anyString(), any(), any())).thenReturn("jwt");

        service.start(SESSION, CLIENT);
        service.start(SESSION, CLIENT);

        ArgumentCaptor<String> jti = ArgumentCaptor.forClass(String.class);
        verify(issuer, org.mockito.Mockito.times(2)).issue(any(), jti.capture(), any(), any());
        assertThat(jti.getAllValues().get(0)).isNotEqualTo(jti.getAllValues().get(1));
    }

    // ---- refresh -----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aBlankRefreshTokenIsInvalidWithoutTouchingTheStore(String token) {
        assertThatThrownBy(() -> service.refresh(token, CLIENT)).isInstanceOf(InvalidSessionException.class);

        verifyNoInteractions(store);
    }

    @Test
    void anUnknownTokenIsInvalid() {
        when(store.findByHash(RefreshTokens.hash("nope"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("nope", CLIENT)).isInstanceOf(InvalidSessionException.class);
    }

    @Test
    void aRevokedTokenIsInvalidWithoutAuditOrNewRevoke() {
        when(store.findByHash(RefreshTokens.hash("t"))).thenReturn(
                Optional.of(stored("t", null, NOW.minusSeconds(5), NOW.plusSeconds(100))));

        assertThatThrownBy(() -> service.refresh("t", CLIENT)).isInstanceOf(InvalidSessionException.class);

        verify(store, never()).markUsed(any(), any());
        verify(store, never()).revokeFamily(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void anExpiredTokenIsInvalid() {
        when(store.findByHash(RefreshTokens.hash("t"))).thenReturn(Optional.of(stored("t", null, null, NOW)));

        assertThatThrownBy(() -> service.refresh("t", CLIENT)).isInstanceOf(InvalidSessionException.class);

        verify(store, never()).markUsed(any(), any());
    }

    @Test
    void refreshRotatesTheTokenAndKeepsTheSessionEnd() {
        Instant sessionEnd = NOW.plus(Duration.ofHours(3));
        RefreshTokenRecord old = stored(" t ".trim(), null, null, sessionEnd);
        when(store.findByHash(RefreshTokens.hash("t"))).thenReturn(Optional.of(old));
        when(store.markUsed(old.id(), NOW)).thenReturn(true);
        when(issuer.issue(eq(SESSION), anyString(), eq(NOW), eq(NOW.plus(ACCESS)))).thenReturn("new-jwt");

        IssuedTokens tokens = service.refresh("  t ", CLIENT);

        assertThat(tokens.accessToken()).isEqualTo("new-jwt");
        assertThat(tokens.refreshToken()).isNotEqualTo("t");
        // the session's end does not move on rotation, and the branch stays the one from login (D33)
        assertThat(tokens.refreshTokenExpiresAt()).isEqualTo(sessionEnd);
        ArgumentCaptor<RefreshTokenRecord> saved = ArgumentCaptor.forClass(RefreshTokenRecord.class);
        verify(store).save(saved.capture());
        assertThat(saved.getValue().familyId()).isEqualTo(old.familyId());
        assertThat(saved.getValue().session()).isEqualTo(SESSION);
        assertThat(saved.getValue().tokenHash()).isEqualTo(RefreshTokens.hash(tokens.refreshToken()));
        verify(store, never()).revokeFamily(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void presentingAUsedTokenAgainRevokesTheWholeFamilyAndAudits() {
        RefreshTokenRecord used = stored("t", NOW.minusSeconds(30), null, NOW.plusSeconds(100));
        when(store.findByHash(RefreshTokens.hash("t"))).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.refresh("t", CLIENT)).isInstanceOf(InvalidSessionException.class);

        verify(store, never()).markUsed(any(), any());
        verify(store).revokeFamily(used.familyId(), NOW);
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::entityId, AuditEntry::ipAddress)
                .containsExactly("REFRESH_REUSE", used.familyId(), "10.0.0.5");
        verify(store, never()).save(any());
    }

    @Test
    void ifAnotherCallWonTheRaceThisOneIsReuseToo() {
        RefreshTokenRecord old = stored("t", null, null, NOW.plusSeconds(100));
        when(store.findByHash(RefreshTokens.hash("t"))).thenReturn(Optional.of(old));
        when(store.markUsed(old.id(), NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.refresh("t", CLIENT)).isInstanceOf(InvalidSessionException.class);

        verify(store).revokeFamily(old.familyId(), NOW);
        verify(audit).record(any(AuditEntry.class));
        verify(store, never()).save(any());
    }

    @Test
    void theRevokeSurvivesThe401() {
        // a domain exception must not roll the transaction back, or the family revoke would be lost
        Transactional tx = TokenService.class.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).contains(DomainException.class);
    }

    // ---- logout ------------------------------------------------------------------------------------------------

    @Test
    void logoutBlacklistsTheAccessTokenAndRevokesTheFamily() {
        RefreshTokenRecord old = stored("t", null, null, NOW.plusSeconds(100));
        when(store.findByHash(RefreshTokens.hash("t"))).thenReturn(Optional.of(old));
        Instant accessExp = NOW.plusSeconds(600);

        service.logout("jti-1", accessExp, "ali", " t ", CLIENT);

        verify(blacklist).revoke("jti-1", accessExp, "LOGOUT");
        verify(store).revokeFamily(old.familyId(), NOW);
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::userName, AuditEntry::userAgent)
                .containsExactly("LOGOUT", "ali", "Firefox");
    }

    @Test
    void logoutWithAnUnknownRefreshTokenStillBlacklistsTheAccessToken() {
        when(store.findByHash(RefreshTokens.hash("unknown"))).thenReturn(Optional.empty());

        service.logout("jti-1", NOW.plusSeconds(600), "ali", "unknown", CLIENT);

        verify(blacklist).revoke(eq("jti-1"), any(), eq("LOGOUT"));
        verify(store, never()).revokeFamily(any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void logoutWithoutARefreshTokenOnlyBlacklistsTheAccessToken(String refreshToken) {
        service.logout("jti-1", NOW.plusSeconds(600), "ali", refreshToken, CLIENT);

        verify(blacklist).revoke(eq("jti-1"), any(), eq("LOGOUT"));
        verifyNoInteractions(store);
    }
}
