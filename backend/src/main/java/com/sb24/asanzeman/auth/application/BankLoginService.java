package com.sb24.asanzeman.auth.application;

import com.sb24.asanzeman.auth.domain.model.AccountName;
import com.sb24.asanzeman.auth.domain.model.AppUser;
import com.sb24.asanzeman.auth.domain.model.BankAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.BankAuthentication;
import com.sb24.asanzeman.auth.domain.model.BankRole;
import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.InvalidBankCredentialsException;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.model.Panel;
import com.sb24.asanzeman.auth.domain.model.Personnel;
import com.sb24.asanzeman.auth.domain.model.PersonnelNotFoundException;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.port.in.BankLoginUseCase;
import com.sb24.asanzeman.auth.domain.port.in.StartSessionUseCase;
import com.sb24.asanzeman.auth.domain.port.in.VerifyCaptchaUseCase;
import com.sb24.asanzeman.auth.domain.port.out.AppUserRepositoryPort;
import com.sb24.asanzeman.auth.domain.port.out.BankAuthenticatorPort;
import com.sb24.asanzeman.auth.domain.port.out.PersonnelDirectoryPort;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 6.2: {@code ARcaptcha → AAA GetRoleWindows → personnelInfo → tokens}. The steps run in this order so that bots cannot
 * use the login to guess passwords (ARcaptcha is first), and nobody gets in without a branch (personnelInfo is
 * mandatory). The bank calls are made outside any database transaction; only starting the session is transactional.
 *
 * <p>A user with both roles gets ADMIN: the access token carries a single {@code role}.
 */
@Service
public class BankLoginService implements BankLoginUseCase {

    private final VerifyCaptchaUseCase captcha;
    private final BankAuthenticatorPort authenticator;
    private final PersonnelDirectoryPort directory;
    private final AppUserRepositoryPort users;
    private final StartSessionUseCase sessions;
    private final Clock clock;
    private final BankLoginSettings settings;

    public BankLoginService(VerifyCaptchaUseCase captcha, BankAuthenticatorPort authenticator,
            PersonnelDirectoryPort directory, AppUserRepositoryPort users, StartSessionUseCase sessions, Clock clock,
            BankLoginSettings settings) {
        this.captcha = captcha;
        this.authenticator = authenticator;
        this.directory = directory;
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public IssuedTokens login(String username, String password, String captchaToken, ClientInfo client) {
        captcha.verify(Panel.BANK, captchaToken);

        BankAuthentication authentication = authenticator.authenticate(username, password);
        if (!authentication.accepted()) {
            throw new InvalidBankCredentialsException();
        }
        if (!authentication.mayEnter()) {
            throw new BankAccessDeniedException();
        }

        AccountName accountName = accountName(username);
        Personnel personnel = directory.find(accountName).orElseThrow(PersonnelNotFoundException::new);
        Role role = authentication.roles().contains(BankRole.ADMIN) ? Role.ADMIN : Role.BRANCH;
        AppUser user = users.upsert(personnel, role, clock.instant());
        return sessions.start(new Session(user.id(), accountName.value(), role, personnel.branchCode()), client);
    }

    /** A login that is nothing but the suffix has no account name: it cannot be a valid user. */
    private AccountName accountName(String username) {
        try {
            return AccountName.fromLogin(username, settings.accountSuffix());
        } catch (IllegalArgumentException e) {
            throw new InvalidBankCredentialsException();
        }
    }
}
