package com.sb24.asanzeman.auth.infrastructure.external;

import com.sb24.asanzeman.auth.domain.model.CaptchaResponse;
import com.sb24.asanzeman.auth.domain.model.Panel;
import com.sb24.asanzeman.auth.domain.port.out.CaptchaVerifierPort;
import com.sb24.asanzeman.integration.arcaptcha.ArcaptchaClient;
import com.sb24.asanzeman.integration.arcaptcha.ArcaptchaVerifyResponse;
import com.sb24.asanzeman.integration.arcaptcha.config.ArcaptchaProperties;
import org.springframework.stereotype.Component;

/** Picks the panel's keys and calls ARcaptcha. {@code verify-enabled=false} accepts every token (local mocks only). */
@Component
public class ArcaptchaVerifierAdapter implements CaptchaVerifierPort {

    private final ArcaptchaClient client;
    private final ArcaptchaProperties properties;

    public ArcaptchaVerifierAdapter(ArcaptchaClient client, ArcaptchaProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public CaptchaResponse verify(Panel panel, String token) {
        if (!properties.verifyEnabled()) {
            return CaptchaResponse.accepted();
        }
        ArcaptchaProperties.Keys keys = switch (panel) {
            case BANK -> properties.bank();
            case AGENCY -> properties.agency();
            case CUSTOMER -> properties.customer();
        };
        ArcaptchaVerifyResponse answer = client.verify(keys.siteKey(), keys.secretKey(), token);
        return new CaptchaResponse(answer.success(), answer.errorCodes());
    }
}
