package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.Panel;

/** Checks the one-time ARcaptcha token of a login. Called first, before AAA or Yaghut (D37). */
public interface VerifyCaptchaUseCase {

    /**
     * @throws com.sb24.asanzeman.auth.domain.model.CaptchaFailedException the token is missing or wrong (400)
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException ARcaptcha is down or misconfigured:
     *         the login is refused (fail closed, D37)
     */
    void verify(Panel panel, String token);
}
