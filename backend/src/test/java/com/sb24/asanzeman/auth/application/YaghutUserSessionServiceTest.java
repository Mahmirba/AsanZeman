package com.sb24.asanzeman.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException;
import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import com.sb24.asanzeman.auth.domain.port.out.RefreshTokenStorePort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class YaghutUserSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final UUID ID = UUID.randomUUID();

    @Mock
    private YaghutSessionStorePort store;
    @Mock
    private RefreshTokenStorePort refreshTokens;

    private YaghutUserSessionService service;

    @BeforeEach
    void setUp() {
        service = new YaghutUserSessionService(store, refreshTokens, new MutableClock(NOW));
    }

    @Test
    void aLiveSessionIsReturned() {
        var session = new YaghutUserSession("S1", NOW.plusSeconds(60));
        when(store.find(ID)).thenReturn(Optional.of(session));

        assertThat(service.require(ID)).isSameAs(session);
        verifyNoMoreInteractions(refreshTokens);
    }

    @Test
    void aSessionWithoutAnEndTimeIsLive() {
        var session = new YaghutUserSession("S1", null);
        when(store.find(ID)).thenReturn(Optional.of(session));

        assertThat(service.require(ID)).isSameAs(session);
    }

    @Test
    void aMissingSessionIsExpired() {
        when(store.find(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(ID)).isInstanceOf(YaghutSessionExpiredException.class);
    }

    @Test
    void anEndedSessionIsRemovedAndTheAsanZemanSessionIsRevoked() {
        when(store.find(ID)).thenReturn(Optional.of(new YaghutUserSession("S1", NOW)));

        assertThatThrownBy(() -> service.require(ID)).isInstanceOf(YaghutSessionExpiredException.class);

        verify(store).delete(ID);
        verify(refreshTokens).revokeFamily(ID, NOW);
    }

    @Test
    void endDropsTheYaghutSessionAndRevokesTheFamily() {
        service.end(ID);

        verify(store).delete(ID);
        verify(refreshTokens).revokeFamily(ID, NOW);
    }

    @Test
    void theCleanUpSurvivesTheExceptionItThrows() {
        assertThat(YaghutUserSessionService.class.getAnnotation(Transactional.class).noRollbackFor())
                .contains(DomainException.class);
    }

    @Test
    void theSessionIdIsNeverPrinted() {
        assertThat(new YaghutUserSession("SECRET", null).toString()).doesNotContain("SECRET");
    }
}
