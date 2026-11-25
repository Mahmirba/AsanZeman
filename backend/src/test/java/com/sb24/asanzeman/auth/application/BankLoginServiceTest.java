package com.sb24.asanzeman.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.AccountName;
import com.sb24.asanzeman.auth.domain.model.AppUser;
import com.sb24.asanzeman.auth.domain.model.BankAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.BankAuthentication;
import com.sb24.asanzeman.auth.domain.model.BankRole;
import com.sb24.asanzeman.auth.domain.model.CaptchaFailedException;
import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.InvalidBankCredentialsException;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.model.Panel;
import com.sb24.asanzeman.auth.domain.model.Personnel;
import com.sb24.asanzeman.auth.domain.model.PersonnelNotFoundException;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.port.in.StartSessionUseCase;
import com.sb24.asanzeman.auth.domain.port.in.VerifyCaptchaUseCase;
import com.sb24.asanzeman.auth.domain.port.out.AppUserRepositoryPort;
import com.sb24.asanzeman.auth.domain.port.out.BankAuthenticatorPort;
import com.sb24.asanzeman.auth.domain.port.out.PersonnelDirectoryPort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final ClientInfo CLIENT = new ClientInfo("10.0.0.5", "Firefox");
    private static final AccountName ALI = new AccountName("ali");
    private static final Personnel PERSONNEL = new Personnel(ALI, "0123", "P1", "ali.u", "Main", "Manager", "Clerk");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final IssuedTokens TOKENS = new IssuedTokens("jwt", NOW.plusSeconds(900), "refresh",
            NOW.plusSeconds(28800));

    @Mock
    private VerifyCaptchaUseCase captcha;
    @Mock
    private BankAuthenticatorPort authenticator;
    @Mock
    private PersonnelDirectoryPort directory;
    @Mock
    private AppUserRepositoryPort users;
    @Mock
    private StartSessionUseCase sessions;

    private BankLoginService service;

    @BeforeEach
    void setUp() {
        service = new BankLoginService(captcha, authenticator, directory, users, sessions,
                new MutableClock(NOW), new BankLoginSettings("@sb24.ir"));
    }

    private IssuedTokens login() {
        return service.login("ali@sb24.ir", "pw", "captcha-token", CLIENT);
    }

    private void acceptWithRoles(BankRole... roles) {
        when(authenticator.authenticate("ali@sb24.ir", "pw"))
                .thenReturn(BankAuthentication.accepted(Set.of(roles)));
        when(directory.find(ALI)).thenReturn(Optional.of(PERSONNEL));
    }

    @Test
    void aBranchUserLogsInAndTheSessionCarriesTheBranchFromPersonnelInfo() {
        acceptWithRoles(BankRole.BRANCH);
        when(users.upsert(PERSONNEL, Role.BRANCH, NOW)).thenReturn(new AppUser(USER_ID, ALI, "0123", Role.BRANCH));
        when(sessions.start(new Session(USER_ID, "ali", Role.BRANCH, "0123"), CLIENT)).thenReturn(TOKENS);

        assertThat(login()).isEqualTo(TOKENS);
    }

    @Test
    void aUserWithBothRolesIsAnAdmin() {
        acceptWithRoles(BankRole.BRANCH, BankRole.ADMIN);
        when(users.upsert(PERSONNEL, Role.ADMIN, NOW)).thenReturn(new AppUser(USER_ID, ALI, "0123", Role.ADMIN));
        when(sessions.start(new Session(USER_ID, "ali", Role.ADMIN, "0123"), CLIENT)).thenReturn(TOKENS);

        assertThat(login()).isEqualTo(TOKENS);
    }

    @Test
    void theStepsRunInTheDocumentedOrder() {
        acceptWithRoles(BankRole.BRANCH);
        when(users.upsert(any(), any(), any())).thenReturn(new AppUser(USER_ID, ALI, "0123", Role.BRANCH));
        when(sessions.start(any(), any())).thenReturn(TOKENS);

        login();

        InOrder order = inOrder(captcha, authenticator, directory, users, sessions);
        order.verify(captcha).verify(Panel.BANK, "captcha-token");
        order.verify(authenticator).authenticate("ali@sb24.ir", "pw");
        order.verify(directory).find(ALI);
        order.verify(users).upsert(PERSONNEL, Role.BRANCH, NOW);
        order.verify(sessions).start(any(), any());
    }

    @Test
    void aFailedCaptchaStopsBeforeAaaIsAsked() {
        Mockito.doThrow(new CaptchaFailedException()).when(captcha).verify(Panel.BANK, "captcha-token");

        assertThatThrownBy(this::login).isInstanceOf(CaptchaFailedException.class);

        verifyNoInteractions(authenticator, directory, users, sessions);
    }

    @Test
    void wrongCredentialsAre401AndStopThere() {
        when(authenticator.authenticate("ali@sb24.ir", "pw")).thenReturn(BankAuthentication.rejected());

        assertThatThrownBy(this::login).isInstanceOf(InvalidBankCredentialsException.class);

        verifyNoInteractions(directory, users, sessions);
    }

    @Test
    void aUserWithoutAMappedRoleIs403() {
        when(authenticator.authenticate("ali@sb24.ir", "pw")).thenReturn(BankAuthentication.accepted(Set.of()));

        assertThatThrownBy(this::login).isInstanceOf(BankAccessDeniedException.class);

        verifyNoInteractions(directory, users, sessions);
    }

    @Test
    void anUnknownPersonIs403AndNobodyGetsInWithoutABranch() {
        when(authenticator.authenticate("ali@sb24.ir", "pw"))
                .thenReturn(BankAuthentication.accepted(Set.of(BankRole.BRANCH)));
        when(directory.find(ALI)).thenReturn(Optional.empty());

        assertThatThrownBy(this::login).isInstanceOf(PersonnelNotFoundException.class);

        verifyNoInteractions(users, sessions);
    }

    @Test
    void aTechnicalFailureOfPersonnelInfoRefusesTheLogin() {
        when(authenticator.authenticate("ali@sb24.ir", "pw"))
                .thenReturn(BankAuthentication.accepted(Set.of(BankRole.BRANCH)));
        var down = new TemporarilyUnavailableException("personnelInfo", new RuntimeException("timeout"));
        when(directory.find(ALI)).thenThrow(down);

        assertThatThrownBy(this::login).isSameAs(down);

        verifyNoInteractions(users, sessions);
    }

    @Test
    void aLoginThatIsOnlyTheSuffixIsNotAValidUser() {
        when(authenticator.authenticate("@sb24.ir", "pw"))
                .thenReturn(BankAuthentication.accepted(Set.of(BankRole.BRANCH)));

        assertThatThrownBy(() -> service.login("@sb24.ir", "pw", "captcha-token", CLIENT))
                .isInstanceOf(InvalidBankCredentialsException.class);

        verify(directory, never()).find(any());
    }
}
