package com.sb24.asanzeman.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.Agency;
import com.sb24.asanzeman.auth.domain.model.AgencyNotRegisteredException;
import com.sb24.asanzeman.auth.domain.model.CaptchaFailedException;
import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.InvalidYaghutCredentialsException;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.model.Panel;
import com.sb24.asanzeman.auth.domain.model.PasswordExpiredException;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.model.TemporarySessionException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccount;
import com.sb24.asanzeman.auth.domain.port.in.StartSessionUseCase;
import com.sb24.asanzeman.auth.domain.port.in.VerifyCaptchaUseCase;
import com.sb24.asanzeman.auth.domain.port.out.AgencyDirectoryPort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutAuthenticatorPort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant YAGHUT_END = NOW.plusSeconds(3600);
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.5", "Firefox");
    private static final UUID AGENCY_ID = UUID.randomUUID();
    private static final IssuedTokens TOKENS = new IssuedTokens("jwt", NOW.plusSeconds(900), "refresh",
            NOW.plusSeconds(3600));

    @Mock
    private VerifyCaptchaUseCase captcha;
    @Mock
    private YaghutAuthenticatorPort yaghut;
    @Mock
    private AgencyDirectoryPort agencies;
    @Mock
    private YaghutSessionStorePort yaghutSessions;
    @Mock
    private StartSessionUseCase sessions;

    private AgencyLoginService service;

    @BeforeEach
    void setUp() {
        service = new AgencyLoginService(captcha, yaghut, agencies, yaghutSessions, sessions, new MutableClock(NOW));
    }

    private static YaghutAccount account(String cif, Instant end, boolean temporary, boolean expired) {
        return new YaghutAccount("Agency", cif, null, "S1", end, temporary, expired);
    }

    private IssuedTokens login() {
        return service.login("agency", "pw", "captcha", CLIENT);
    }

    private void yaghutAnswers(YaghutAccount account) {
        when(yaghut.login("agency", "pw")).thenReturn(account);
    }

    private void registered(String name) {
        when(agencies.findByCif("9001")).thenReturn(Optional.of(new Agency(AGENCY_ID, "9001", name, true)));
        when(sessions.start(any(), any(UUID.class), any(), any())).thenReturn(TOKENS);
    }

    @Test
    void aRegisteredAgencyGetsASessionCappedAtYaghutsEndAndItsYaghutSessionIsKept() {
        yaghutAnswers(account(" 9001 ", YAGHUT_END, false, false));
        registered("Sky Travel");
        ArgumentCaptor<UUID> family = ArgumentCaptor.forClass(UUID.class);

        assertThat(login()).isEqualTo(TOKENS);

        InOrder order = inOrder(captcha, yaghut, yaghutSessions, sessions);
        order.verify(captcha).verify(Panel.AGENCY, "captcha");
        order.verify(yaghut).login("agency", "pw");
        order.verify(yaghutSessions).save(family.capture(), eq("S1"), eq(YAGHUT_END));
        order.verify(sessions).start(new Session(AGENCY_ID, "Sky Travel", Role.AGENCY, null, "agency"), family.getValue(),
                YAGHUT_END, CLIENT);
    }

    @Test
    void aSessionWithoutAYaghutEndHasNoCap() {
        yaghutAnswers(account("9001", null, false, false));
        registered("Sky");

        login();

        verify(yaghutSessions).save(any(), eq("S1"), isNull());
    }

    @Test
    void aLongAgencyNameIsCutToTheStoredLength() {
        yaghutAnswers(account("9001", null, false, false));
        registered("x".repeat(150));

        login();

        ArgumentCaptor<Session> session = ArgumentCaptor.forClass(Session.class);
        verify(sessions).start(session.capture(), any(UUID.class), any(), any());
        assertThat(session.getValue().subjectName()).hasSize(100);
    }

    @Test
    void aWrongCaptchaStopsBeforeYaghut() {
        doThrow(new CaptchaFailedException()).when(captcha).verify(Panel.AGENCY, "captcha");

        assertThatThrownBy(this::login).isInstanceOf(CaptchaFailedException.class);
        verifyNoInteractions(yaghut, agencies, yaghutSessions, sessions);
    }

    @Test
    void yaghutRefusalsPassThroughAndNothingIsStored() {
        when(yaghut.login("agency", "pw")).thenThrow(new InvalidYaghutCredentialsException());

        assertThatThrownBy(this::login).isInstanceOf(InvalidYaghutCredentialsException.class);
        verifyNoInteractions(agencies, yaghutSessions, sessions);
    }

    @Test
    void aTemporarySessionIsRefused() {
        yaghutAnswers(account("9001", YAGHUT_END, true, false));

        assertThatThrownBy(this::login).isInstanceOf(TemporarySessionException.class);
        verifyNoInteractions(agencies, yaghutSessions, sessions);
    }

    @Test
    void anExpiredPasswordIsRefused() {
        yaghutAnswers(account("9001", YAGHUT_END, false, true));

        assertThatThrownBy(this::login).isInstanceOf(PasswordExpiredException.class);
        verifyNoInteractions(agencies, yaghutSessions, sessions);
    }

    @Test
    void aMissingOrBlankCifIsNotAnAgency() {
        yaghutAnswers(account(null, YAGHUT_END, false, false));
        assertThatThrownBy(this::login).isInstanceOf(AgencyNotRegisteredException.class);

        yaghutAnswers(account("  ", YAGHUT_END, false, false));
        assertThatThrownBy(this::login).isInstanceOf(AgencyNotRegisteredException.class);
        verifyNoInteractions(agencies, yaghutSessions, sessions);
    }

    @Test
    void anUnknownOrInactiveAgencyIsRefused() {
        yaghutAnswers(account("9001", YAGHUT_END, false, false));
        when(agencies.findByCif("9001")).thenReturn(Optional.empty());
        assertThatThrownBy(this::login).isInstanceOf(AgencyNotRegisteredException.class);

        when(agencies.findByCif("9001")).thenReturn(Optional.of(new Agency(AGENCY_ID, "9001", "Sky", false)));
        assertThatThrownBy(this::login).isInstanceOf(AgencyNotRegisteredException.class);
        verifyNoInteractions(yaghutSessions, sessions);
    }

    @Test
    void aYaghutSessionThatHasAlreadyEndedIsTechnical() {
        yaghutAnswers(account("9001", NOW, false, false));
        when(agencies.findByCif("9001")).thenReturn(Optional.of(new Agency(AGENCY_ID, "9001", "Sky", true)));

        assertThatThrownBy(this::login).isInstanceOf(TemporarilyUnavailableException.class);
        verifyNoInteractions(yaghutSessions, sessions);
    }
}
