package com.sb24.asanzeman.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.CaptchaFailedException;
import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.CustomerChallenge;
import com.sb24.asanzeman.auth.domain.model.CustomerMobileMissingException;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.model.OtpChallenge;
import com.sb24.asanzeman.auth.domain.model.OtpExpiredException;
import com.sb24.asanzeman.auth.domain.model.OtpLockedException;
import com.sb24.asanzeman.auth.domain.model.OtpWrongException;
import com.sb24.asanzeman.auth.domain.model.Panel;
import com.sb24.asanzeman.auth.domain.model.PasswordExpiredException;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.model.SuspectedCustomerException;
import com.sb24.asanzeman.auth.domain.model.TemporarySessionException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccount;
import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import com.sb24.asanzeman.auth.domain.port.in.StartSessionUseCase;
import com.sb24.asanzeman.auth.domain.port.in.VerifyCaptchaUseCase;
import com.sb24.asanzeman.auth.domain.port.out.CustomerMobilePort;
import com.sb24.asanzeman.auth.domain.port.out.OtpChallengeStorePort;
import com.sb24.asanzeman.auth.domain.port.out.OtpGatewayPort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutAuthenticatorPort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import com.sb24.asanzeman.support.MutableClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final OtpSettings SETTINGS = new OtpSettings(Duration.ofSeconds(120), 3, Duration.ofMinutes(15),
            Duration.ofMinutes(30));
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.5", "Firefox");
    private static final String CODE = "1000000011";

    @Mock
    private VerifyCaptchaUseCase captcha;
    @Mock
    private YaghutAuthenticatorPort yaghut;
    @Mock
    private CustomerMobilePort mobiles;
    @Mock
    private OtpGatewayPort gateway;
    @Mock
    private OtpChallengeStorePort challenges;
    @Mock
    private YaghutSessionStorePort yaghutSessions;
    @Mock
    private StartSessionUseCase sessions;

    private CustomerLoginService service;

    private final SimpleMeterRegistry metrics =
            new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        service = new CustomerLoginService(captcha, yaghut, mobiles, gateway, challenges, yaghutSessions, sessions,
                new MutableClock(NOW), SETTINGS, metrics);
    }

    private static YaghutAccount account(String code, boolean temporary, boolean expired) {
        return new YaghutAccount("Ali", null, code, "JS-1", NOW.plusSeconds(3600), temporary, expired);
    }

    // ---- step 1 ---------------------------------------------------------------------------------------------

    @Test
    void theCodeGoesToTheMobileOfTheRequestAndTheYaghutSessionIsKeptForTheNewSession() {
        when(yaghut.login("ali", "pw")).thenReturn(account(" " + CODE + " ", false, false));
        when(mobiles.mobileOf(any(), eq(CODE))).thenReturn(Optional.of("09121234567"));
        when(gateway.send("09121234567")).thenReturn("otp-1");

        CustomerChallenge challenge = service.login("ali", "pw", "captcha");

        verify(captcha).verify(Panel.CUSTOMER, "captcha");
        var saved = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challenges).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(challenge.challengeId());
        assertThat(saved.getValue().nationalCode()).isEqualTo(CODE);
        assertThat(saved.getValue().otpId()).isEqualTo("otp-1");
        assertThat(saved.getValue().sentAt()).isEqualTo(NOW);
        assertThat(saved.getValue().verified()).isFalse();
        verify(yaghutSessions).save(saved.getValue().familyId(), "JS-1", NOW.plusSeconds(3600));
        assertThat(challenge.expiresInSeconds()).isEqualTo(120);
        assertThat(metrics.get("azs.otp.sent").counter().count()).isEqualTo(1.0);
    }

    @Test
    void aFailedCaptchaStopsBeforeYaghut() {
        Mockito.doThrow(new CaptchaFailedException()).when(captcha).verify(Panel.CUSTOMER, "bad");

        assertThatThrownBy(() -> service.login("ali", "pw", "bad")).isInstanceOf(CaptchaFailedException.class);
        verifyNoInteractions(yaghut, gateway);
        assertThat(metrics.find("azs.otp.sent").counter()).isNull();
    }

    @Test
    void aTemporaryYaghutSessionIsRefused() {
        when(yaghut.login("ali", "pw")).thenReturn(account(CODE, true, false));

        assertThatThrownBy(() -> service.login("ali", "pw", "c")).isInstanceOf(TemporarySessionException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void anExpiredPasswordIsRefused() {
        when(yaghut.login("ali", "pw")).thenReturn(account(CODE, false, true));

        assertThatThrownBy(() -> service.login("ali", "pw", "c")).isInstanceOf(PasswordExpiredException.class);
    }

    @Test
    void aYaghutUserWithoutANationalCodeIsNotACustomer() {
        when(yaghut.login("ali", "pw")).thenReturn(account(null, false, false));

        assertThatThrownBy(() -> service.login("ali", "pw", "c")).isInstanceOf(SuspectedCustomerException.class);
        verifyNoInteractions(mobiles, gateway);
    }

    @Test
    void aBlankNationalCodeIsNotACustomerEither() {
        when(yaghut.login("ali", "pw")).thenReturn(account("  ", false, false));

        assertThatThrownBy(() -> service.login("ali", "pw", "c")).isInstanceOf(SuspectedCustomerException.class);
        verifyNoInteractions(mobiles);
    }

    @Test
    void aCustomerWithoutAMobileCannotGetACode() {
        when(yaghut.login("ali", "pw")).thenReturn(account(CODE, false, false));
        when(mobiles.mobileOf(any(), eq(CODE))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ali", "pw", "c")).isInstanceOf(CustomerMobileMissingException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void aLockedCustomerGetsNoNewCode() {
        when(yaghut.login("ali", "pw")).thenReturn(account(CODE, false, false));
        when(mobiles.mobileOf(any(), eq(CODE))).thenReturn(Optional.of("09121234567"));
        when(challenges.isLocked(CODE, NOW)).thenReturn(true);

        assertThatThrownBy(() -> service.login("ali", "pw", "c")).isInstanceOf(OtpLockedException.class);
        verifyNoInteractions(gateway);
        verify(challenges, never()).save(any());
    }

    @Test
    void aFailureToSendTheCodeKeepsNothing() {
        when(yaghut.login("ali", "pw")).thenReturn(account(CODE, false, false));
        when(mobiles.mobileOf(any(), eq(CODE))).thenReturn(Optional.of("09121234567"));
        when(gateway.send("09121234567")).thenThrow(new TemporarilyUnavailableException("Notifier", null));

        assertThatThrownBy(() -> service.login("ali", "pw", "c")).isInstanceOf(TemporarilyUnavailableException.class);
        verifyNoInteractions(yaghutSessions);
        verify(challenges, never()).save(any());
    }

    // ---- step 2 ---------------------------------------------------------------------------------------------

    private OtpChallenge challenge(Instant sentAt, boolean verified) {
        return new OtpChallenge(UUID.randomUUID(), CODE, "09121234567", "otp-1", UUID.randomUUID(), sentAt, verified);
    }

    @Test
    void aRightCodeStartsA30MinuteSessionForTheCustomerUnderTheSavedFamily() {
        OtpChallenge c = challenge(NOW.minusSeconds(30), false);
        IssuedTokens tokens = new IssuedTokens("jwt", NOW.plusSeconds(1800), null, NOW.plusSeconds(1800));
        when(challenges.find(c.id())).thenReturn(Optional.of(c));
        when(gateway.validate("09121234567", "otp-1", "1234")).thenReturn(true);
        when(challenges.markVerified(c.id())).thenReturn(true);
        var session = ArgumentCaptor.forClass(Session.class);
        when(sessions.start(session.capture(), eq(c.familyId()),
                eq(NOW.plusSeconds(1800)), eq(CLIENT)))
                .thenReturn(tokens);

        assertThat(service.verifyOtp(c.id(), "1234", CLIENT)).isSameAs(tokens);

        assertThat(session.getValue().role()).isEqualTo(Role.CUSTOMER);
        assertThat(session.getValue().subjectName()).isEqualTo(CODE);
        assertThat(session.getValue().branchCode()).isNull();
        verify(challenges).clearWrong(CODE);
    }

    @Test
    void theSessionNeverOutlastsTheYaghutSessionItsCallsDependOn() {
        OtpChallenge c = challenge(NOW, false);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));
        when(gateway.validate("09121234567", "otp-1", "1234")).thenReturn(true);
        when(challenges.markVerified(c.id())).thenReturn(true);
        when(yaghutSessions.find(c.familyId()))
                .thenReturn(Optional.of(new YaghutUserSession("JS-1", NOW.plusSeconds(600))));

        service.verifyOtp(c.id(), "1234", CLIENT);

        verify(sessions).start(any(), eq(c.familyId()), eq(NOW.plusSeconds(600)), eq(CLIENT));
    }

    @Test
    void aLongerYaghutSessionDoesNotStretchTheCustomerSession() {
        OtpChallenge c = challenge(NOW, false);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));
        when(gateway.validate("09121234567", "otp-1", "1234")).thenReturn(true);
        when(challenges.markVerified(c.id())).thenReturn(true);
        when(yaghutSessions.find(c.familyId()))
                .thenReturn(Optional.of(new YaghutUserSession("JS-1", NOW.plusSeconds(7200))));

        service.verifyOtp(c.id(), "1234", CLIENT);

        verify(sessions).start(any(), eq(c.familyId()), eq(NOW.plusSeconds(1800)), eq(CLIENT));
    }

    @Test
    void aYaghutUserWhoseCodeIsNotANationalCodeIsNotACustomer() {
        when(yaghut.login("agency", "pw")).thenReturn(account("9001", false, false));

        assertThatThrownBy(() -> service.login("agency", "pw", "c")).isInstanceOf(SuspectedCustomerException.class);
        verifyNoInteractions(mobiles, gateway);
    }

    @Test
    void theSameCustomerAlwaysGetsTheSameSubjectId() {
        OtpChallenge c = challenge(NOW, false);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));
        when(gateway.validate("09121234567", "otp-1", "1234")).thenReturn(true);
        when(challenges.markVerified(c.id())).thenReturn(true);
        var session = ArgumentCaptor.forClass(Session.class);

        service.verifyOtp(c.id(), "1234", CLIENT);
        service.verifyOtp(c.id(), "1234", CLIENT);

        verify(sessions, Mockito.times(2)).start(session.capture(), any(), any(), any());
        assertThat(session.getAllValues().get(0).subjectId()).isEqualTo(session.getAllValues().get(1).subjectId());
    }

    @Test
    void anUnknownCodeIdIsExpired() {
        UUID id = UUID.randomUUID();
        when(challenges.find(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp(id, "1234", CLIENT)).isInstanceOf(OtpExpiredException.class);
        verifyNoInteractions(gateway, sessions);
    }

    @Test
    void aCodeThatWasUsedIsExpired() {
        OtpChallenge c = challenge(NOW, true);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.verifyOtp(c.id(), "1234", CLIENT)).isInstanceOf(OtpExpiredException.class);
        verifyNoInteractions(gateway, sessions);
    }

    @Test
    void aCodeOlderThanItsValidityIsExpiredAndTheSmsCentreIsNotAsked() {
        OtpChallenge c = challenge(NOW.minusSeconds(120), false);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.verifyOtp(c.id(), "1234", CLIENT)).isInstanceOf(OtpExpiredException.class);
        verifyNoInteractions(gateway, sessions);
    }

    @Test
    void aCodeJustInsideItsValidityStillWorks() {
        OtpChallenge c = challenge(NOW.minusSeconds(119), false);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));
        when(gateway.validate("09121234567", "otp-1", "1234")).thenReturn(true);
        when(challenges.markVerified(c.id())).thenReturn(true);

        service.verifyOtp(c.id(), "1234", CLIENT);

        verify(sessions).start(any(), any(), any(), any());
    }

    @Test
    void aLockedCustomerCannotCheckAnyCode() {
        OtpChallenge c = challenge(NOW, false);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));
        when(challenges.isLocked(CODE, NOW)).thenReturn(true);

        assertThatThrownBy(() -> service.verifyOtp(c.id(), "1234", CLIENT)).isInstanceOf(OtpLockedException.class);
        verifyNoInteractions(gateway, sessions);
    }

    @Test
    void aWrongCodeIsCountedAndTheCustomerMayTryAgain() {
        OtpChallenge c = challenge(NOW, false);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));
        when(gateway.validate("09121234567", "otp-1", "0000")).thenReturn(false);
        when(challenges.recordWrong(CODE, NOW, 3, Duration.ofMinutes(15))).thenReturn(false);

        assertThatThrownBy(() -> service.verifyOtp(c.id(), "0000", CLIENT)).isInstanceOf(OtpWrongException.class);
        verify(challenges, never()).markVerified(any());
        verifyNoInteractions(sessions);
    }

    @Test
    void theWrongCodeThatReachesTheLimitLocksTheCustomer() {
        OtpChallenge c = challenge(NOW, false);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));
        when(gateway.validate("09121234567", "otp-1", "0000")).thenReturn(false);
        when(challenges.recordWrong(CODE, NOW, 3, Duration.ofMinutes(15))).thenReturn(true);

        assertThatThrownBy(() -> service.verifyOtp(c.id(), "0000", CLIENT)).isInstanceOf(OtpLockedException.class);
    }

    @Test
    void aCodeUsedByAParallelRequestFirstIsExpired() {
        OtpChallenge c = challenge(NOW, false);
        when(challenges.find(c.id())).thenReturn(Optional.of(c));
        when(gateway.validate("09121234567", "otp-1", "1234")).thenReturn(true);
        when(challenges.markVerified(c.id())).thenReturn(false);

        assertThatThrownBy(() -> service.verifyOtp(c.id(), "1234", CLIENT)).isInstanceOf(OtpExpiredException.class);
        verifyNoInteractions(sessions);
        verify(challenges, never()).clearWrong(any());
    }

    @Test
    void theRefusalsHaveTheRightStatusKinds() {
        assertThat(new CustomerMobileMissingException().kind()).isEqualTo(DomainException.Kind.FORBIDDEN);
        assertThat(new OtpExpiredException().kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
        assertThat(new OtpWrongException().kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
        assertThat(new OtpLockedException().kind()).isEqualTo(DomainException.Kind.FORBIDDEN);
    }
}
