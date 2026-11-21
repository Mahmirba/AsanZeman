package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;

/** Agency login (5.4): ARcaptcha, then Yaghut {@code loginStatic}, then the agency by {@code cif}, then tokens. */
public interface AgencyLoginUseCase {

    /**
     * The session ends at the earlier of the refresh-token lifetime (8 hours) and Yaghut's own session end, so it
     * never outlives the Yaghut session the agency's calls depend on.
     *
     * @throws com.sb24.asanzeman.auth.domain.model.CaptchaFailedException               400
     * @throws com.sb24.asanzeman.auth.domain.model.InvalidYaghutCredentialsException    401
     * @throws com.sb24.asanzeman.auth.domain.model.SuspectedCustomerException           403
     * @throws com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException          403
     * @throws com.sb24.asanzeman.auth.domain.model.TemporarySessionException            403
     * @throws com.sb24.asanzeman.auth.domain.model.PasswordExpiredException             403
     * @throws com.sb24.asanzeman.auth.domain.model.AgencyNotRegisteredException         403
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException           503
     */
    IssuedTokens login(String username, String password, String captchaToken, ClientInfo client);
}
