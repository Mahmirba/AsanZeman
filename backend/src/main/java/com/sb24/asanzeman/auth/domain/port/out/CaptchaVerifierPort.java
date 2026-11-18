package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.CaptchaResponse;
import com.sb24.asanzeman.auth.domain.model.Panel;

/** Asks ARcaptcha whether a token is valid, using the panel's own keys. */
public interface CaptchaVerifierPort {

    /**
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException on timeout, network error or 5xx
     */
    CaptchaResponse verify(Panel panel, String token);
}
