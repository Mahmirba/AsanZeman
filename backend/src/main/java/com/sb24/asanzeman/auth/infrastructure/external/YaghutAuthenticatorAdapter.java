package com.sb24.asanzeman.auth.infrastructure.external;

import com.sb24.asanzeman.auth.domain.model.InvalidYaghutCredentialsException;
import com.sb24.asanzeman.auth.domain.model.SuspectedCustomerException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccount;
import com.sb24.asanzeman.auth.domain.port.out.YaghutAuthenticatorPort;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutException;
import com.sb24.asanzeman.integration.yaghut.YaghutLogin;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Yaghut {@code loginStatic} for agencies and customers (5.4). Business faults become the domain's login refusals;
 * any other fault at login (an invalid session or customer cannot happen here) is technical.
 *
 * <p>The password is expired when {@code passwordExpirationBean} is an object with {@code expired: true}. The bean's
 * real shape is confirmed in UAT; an unreadable bean is read as "not expired", never as a refusal.
 */
@Component
public class YaghutAuthenticatorAdapter implements YaghutAuthenticatorPort {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final YaghutClient client;

    public YaghutAuthenticatorAdapter(YaghutClient client) {
        this.client = client;
    }

    @Override
    public YaghutAccount login(String username, String password) {
        YaghutLogin login;
        try {
            login = client.login(username, password);
        } catch (YaghutException e) {
            throw switch (e.fault()) {
                case INVALID_CREDENTIALS -> new InvalidYaghutCredentialsException();
                case SUSPECTED_CUSTOMER -> new SuspectedCustomerException();
                case SERVICE_ACCESS_DENIED -> new YaghutAccessDeniedException();
                default -> new TemporarilyUnavailableException("Yaghut", e);
            };
        }
        return new YaghutAccount(login.name(), login.cif(), login.code(), login.session().jsessionId(),
                login.session().expiresAt(),
                login.temporarySessionId() != null && !login.temporarySessionId().isBlank(),
                expired(login.passwordExpirationJson()));
    }

    private static boolean expired(String json) {
        if (json == null) {
            return false;
        }
        try {
            return JSON.readTree(json).path("expired").asBoolean(false);
        } catch (JacksonException e) {
            return false;
        }
    }
}
