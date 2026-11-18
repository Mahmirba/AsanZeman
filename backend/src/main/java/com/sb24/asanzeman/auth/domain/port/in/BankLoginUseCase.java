package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;

/** Bank user login (6.2): ARcaptcha, then AAA, then personnelInfo, then tokens. */
public interface BankLoginUseCase {

    /**
     * @param username     what the user typed, possibly with the {@code @sb24.ir} suffix
     * @param captchaToken the one-time ARcaptcha token
     * @throws com.sb24.asanzeman.auth.domain.model.CaptchaFailedException           400
     * @throws com.sb24.asanzeman.auth.domain.model.InvalidBankCredentialsException  401
     * @throws com.sb24.asanzeman.auth.domain.model.BankAccessDeniedException        403, no mapped role
     * @throws com.sb24.asanzeman.auth.domain.model.PersonnelNotFoundException       403
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException       503, a bank service is down
     */
    IssuedTokens login(String username, String password, String captchaToken, ClientInfo client);
}
