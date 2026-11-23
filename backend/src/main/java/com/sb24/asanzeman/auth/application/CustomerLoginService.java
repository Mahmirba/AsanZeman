package com.sb24.asanzeman.auth.application;

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
import com.sb24.asanzeman.auth.domain.port.in.CustomerLoginUseCase;
import com.sb24.asanzeman.auth.domain.port.in.StartSessionUseCase;
import com.sb24.asanzeman.auth.domain.port.in.VerifyCaptchaUseCase;
import com.sb24.asanzeman.auth.domain.port.out.CustomerMobilePort;
import com.sb24.asanzeman.auth.domain.port.out.OtpChallengeStorePort;
import com.sb24.asanzeman.auth.domain.port.out.OtpGatewayPort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutAuthenticatorPort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
import com.sb24.asanzeman.shared.model.NationalCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 6.3: {@code ARcaptcha → Yaghut loginStatic → SMS code} and then {@code code check → session}. The Yaghut session is
 * kept under the family the customer's session will use, so their later Yaghut calls (deposits) use their own session.
 *
 * <p>The customer is the Yaghut user's national code ({@code code}), and the SMS code goes to the mobile the bank holds
 * for them, read with their own new session. A customer with no request simply sees an empty list. The session's
 * {@code sub} is the national code, which is how the customer endpoints know whose requests to show. No call here runs
 * in a database transaction: each store call is one statement, and the Yaghut and SMS calls must not hold a
 * connection.
 */
@Service
public class CustomerLoginService implements CustomerLoginUseCase {

    private final VerifyCaptchaUseCase captcha;
    private final YaghutAuthenticatorPort yaghut;
    private final CustomerMobilePort mobiles;
    private final OtpGatewayPort gateway;
    private final OtpChallengeStorePort challenges;
    private final YaghutSessionStorePort yaghutSessions;
    private final StartSessionUseCase sessions;
    private final Clock clock;
    private final OtpSettings settings;

    public CustomerLoginService(VerifyCaptchaUseCase captcha, YaghutAuthenticatorPort yaghut,
            CustomerMobilePort mobiles, OtpGatewayPort gateway, OtpChallengeStorePort challenges,
            YaghutSessionStorePort yaghutSessions, StartSessionUseCase sessions, Clock clock, OtpSettings settings) {
        this.captcha = captcha;
        this.yaghut = yaghut;
        this.mobiles = mobiles;
        this.gateway = gateway;
        this.challenges = challenges;
        this.yaghutSessions = yaghutSessions;
        this.sessions = sessions;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public CustomerChallenge login(String username, String password, String captchaToken) {
        captcha.verify(Panel.CUSTOMER, captchaToken);

        YaghutAccount account = yaghut.login(username, password);
        if (account.temporarySession()) {
            throw new TemporarySessionException();
        }
        if (account.passwordExpired()) {
            throw new PasswordExpiredException();
        }
        // a Yaghut user whose code is not a national code (an agency, say) is not a customer
        String nationalCode = NationalCode.parse(account.code() == null ? "" : account.code().trim())
                .orElseThrow(SuspectedCustomerException::new).value();
        String mobile = mobiles.mobileOf(account, nationalCode).orElseThrow(CustomerMobileMissingException::new);
        Instant now = clock.instant();
        if (challenges.isLocked(nationalCode, now)) {
            throw new OtpLockedException();
        }

        String otpId = gateway.send(mobile);
        UUID family = UUID.randomUUID();
        yaghutSessions.save(family, account.sessionId(), account.sessionExpiresAt());
        UUID id = UUID.randomUUID();
        challenges.save(new OtpChallenge(id, nationalCode, mobile, otpId, family, now, false));
        return new CustomerChallenge(id, settings.validity().toSeconds());
    }

    @Override
    public IssuedTokens verifyOtp(UUID challengeId, String code, ClientInfo client) {
        Instant now = clock.instant();
        OtpChallenge challenge = challenges.find(challengeId).filter(c -> !c.verified())
                .orElseThrow(OtpExpiredException::new);
        if (challenges.isLocked(challenge.nationalCode(), now)) {
            throw new OtpLockedException();
        }
        if (!challenge.sentAt().plus(settings.validity()).isAfter(now)) {
            throw new OtpExpiredException();
        }
        if (!gateway.validate(challenge.mobile(), challenge.otpId(), code)) {
            boolean locked = challenges.recordWrong(challenge.nationalCode(), now, settings.maxWrongAttempts(),
                    settings.lockout());
            throw locked ? new OtpLockedException() : new OtpWrongException();
        }
        if (!challenges.markVerified(challenge.id())) {
            throw new OtpExpiredException(); // the same code was used by another request at the same time
        }
        challenges.clearWrong(challenge.nationalCode());

        // no refresh token can renew it, so the session must not outlast the Yaghut session its calls depend on
        Instant end = now.plus(settings.sessionTtl());
        Instant yaghutEnd = yaghutSessions.find(challenge.familyId()).map(YaghutUserSession::expiresAt).orElse(null);
        if (yaghutEnd != null && yaghutEnd.isBefore(end)) {
            end = yaghutEnd;
        }
        UUID subjectId = UUID.nameUUIDFromBytes(("customer:" + challenge.nationalCode()).getBytes(StandardCharsets.UTF_8));
        return sessions.start(new Session(subjectId, challenge.nationalCode(), Role.CUSTOMER, null),
                challenge.familyId(), end, client);
    }
}
