package com.sb24.asanzeman.auth.application;

import com.sb24.asanzeman.auth.domain.model.Agency;
import com.sb24.asanzeman.auth.domain.model.AgencyNotRegisteredException;
import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.model.Panel;
import com.sb24.asanzeman.auth.domain.model.PasswordExpiredException;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.model.TemporarySessionException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccount;
import com.sb24.asanzeman.auth.domain.port.in.AgencyLoginUseCase;
import com.sb24.asanzeman.auth.domain.port.in.StartSessionUseCase;
import com.sb24.asanzeman.auth.domain.port.in.VerifyCaptchaUseCase;
import com.sb24.asanzeman.auth.domain.port.out.AgencyDirectoryPort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutAuthenticatorPort;
import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 5.4: {@code ARcaptcha → Yaghut loginStatic → login checks → agency by cif → tokens}. ARcaptcha is first so the login
 * cannot be used to guess passwords. The Yaghut session is kept on the server under the new session's family, and the
 * Asan Zeman session is capped at Yaghut's own end time, so it ends before a Yaghut call can fail. The agency's
 * password is never stored, so the session cannot be renewed past that end.
 *
 * <p>The Yaghut call runs outside any database transaction. If starting the session fails after the Yaghut session was
 * stored, the stored row is unreachable (its family never existed) and is purged with the other expired rows.
 */
@Service
public class AgencyLoginService implements AgencyLoginUseCase {

    private static final String YAGHUT = "Yaghut";
    /** {@code refresh_tokens.subject_name} holds 100 characters; {@code agency.name} holds 200. */
    private static final int SUBJECT_NAME_MAX = 100;

    private final VerifyCaptchaUseCase captcha;
    private final YaghutAuthenticatorPort yaghut;
    private final AgencyDirectoryPort agencies;
    private final YaghutSessionStorePort yaghutSessions;
    private final StartSessionUseCase sessions;
    private final Clock clock;

    public AgencyLoginService(VerifyCaptchaUseCase captcha, YaghutAuthenticatorPort yaghut, AgencyDirectoryPort agencies,
            YaghutSessionStorePort yaghutSessions, StartSessionUseCase sessions, Clock clock) {
        this.captcha = captcha;
        this.yaghut = yaghut;
        this.agencies = agencies;
        this.yaghutSessions = yaghutSessions;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public IssuedTokens login(String username, String password, String captchaToken, ClientInfo client) {
        captcha.verify(Panel.AGENCY, captchaToken);

        YaghutAccount account = yaghut.login(username, password);
        if (account.temporarySession()) {
            throw new TemporarySessionException();
        }
        if (account.passwordExpired()) {
            throw new PasswordExpiredException();
        }
        Agency agency = agency(account);

        Instant yaghutEnd = account.sessionExpiresAt();
        if (yaghutEnd != null && !yaghutEnd.isAfter(clock.instant())) {
            // Yaghut gave us a session that has already ended: nothing to build a session on
            throw new TemporarilyUnavailableException(YAGHUT,
                    new IllegalStateException("loginStatic returned a session that has already expired"));
        }
        UUID family = UUID.randomUUID();
        yaghutSessions.save(family, account.sessionId(), yaghutEnd);
        return sessions.start(new Session(agency.id(), subjectName(agency), Role.AGENCY, null), family, yaghutEnd,
                client);
    }

    private Agency agency(YaghutAccount account) {
        if (account.cif() == null || account.cif().isBlank()) {
            throw new AgencyNotRegisteredException();
        }
        Agency agency = agencies.findByCif(account.cif().trim()).orElseThrow(AgencyNotRegisteredException::new);
        if (!agency.active()) {
            throw new AgencyNotRegisteredException();
        }
        return agency;
    }

    private static String subjectName(Agency agency) {
        String name = agency.name();
        return name.length() <= SUBJECT_NAME_MAX ? name : name.substring(0, SUBJECT_NAME_MAX);
    }
}
