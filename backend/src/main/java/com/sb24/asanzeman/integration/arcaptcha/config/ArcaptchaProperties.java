package com.sb24.asanzeman.integration.arcaptcha.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code azs.integration.arcaptcha.*} (D36, D37, D38). Keys are per panel. */
@ConfigurationProperties("azs.integration.arcaptcha")
public record ArcaptchaProperties(
        String verifyUrl,
        boolean verifyEnabled,
        Keys bank,
        Keys agency,
        Keys customer,
        Duration connectTimeout,
        Duration readTimeout,
        Tls tls) {

    public record Keys(String siteKey, String secretKey) {
    }

    public record Tls(boolean skipVerify) {
    }
}
