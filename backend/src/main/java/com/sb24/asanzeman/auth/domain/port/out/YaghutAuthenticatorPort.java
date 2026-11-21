package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.YaghutAccount;

/** Logs a user in at Yaghut ({@code loginStatic}, 5.4). Agencies and customers use their own credentials. */
public interface YaghutAuthenticatorPort {

    /**
     * @throws com.sb24.asanzeman.auth.domain.model.InvalidYaghutCredentialsException wrong user name or password
     * @throws com.sb24.asanzeman.auth.domain.model.SuspectedCustomerException        Yaghut refuses this user
     * @throws com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException       the service is not active for them
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException        any technical failure
     */
    YaghutAccount login(String username, String password);
}
