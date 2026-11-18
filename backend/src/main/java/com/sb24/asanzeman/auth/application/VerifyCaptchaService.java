package com.sb24.asanzeman.auth.application;

import com.sb24.asanzeman.auth.domain.model.CaptchaFailedException;
import com.sb24.asanzeman.auth.domain.model.CaptchaResponse;
import com.sb24.asanzeman.auth.domain.model.CaptchaVerdict;
import com.sb24.asanzeman.auth.domain.model.Panel;
import com.sb24.asanzeman.auth.domain.port.in.VerifyCaptchaUseCase;
import com.sb24.asanzeman.auth.domain.port.out.CaptchaVerifierPort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 6.5: a token is never cached or reused, so every login is one call. A blank token is refused without calling
 * ARcaptcha. If ARcaptcha cannot be reached, the technical exception passes through and the login is refused (D37).
 */
@Service
public class VerifyCaptchaService implements VerifyCaptchaUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerifyCaptchaService.class);
    static final String SERVICE = "ARcaptcha";

    private final CaptchaVerifierPort verifier;

    public VerifyCaptchaService(CaptchaVerifierPort verifier) {
        this.verifier = verifier;
    }

    @Override
    public void verify(Panel panel, String token) {
        if (token == null || token.isBlank()) {
            throw new CaptchaFailedException();
        }
        CaptchaResponse response = verifier.verify(panel, token.trim());
        CaptchaVerdict verdict = response.verdict();
        if (verdict == CaptchaVerdict.WRONG_TOKEN) {
            throw new CaptchaFailedException();
        }
        if (verdict == CaptchaVerdict.MISCONFIGURED) {
            // ops alert: the keys or the request are wrong, and no user action can fix it
            log.error("ARcaptcha configuration error for the {} panel: {}", panel, response.errorCodes());
            throw new TemporarilyUnavailableException(SERVICE,
                    new IllegalStateException("ARcaptcha rejected our configuration: " + response.errorCodes()));
        }
    }
}
