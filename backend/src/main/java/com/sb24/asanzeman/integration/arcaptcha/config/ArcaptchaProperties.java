package com.sb24.asanzeman.integration.arcaptcha.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code azs.integration.arcaptcha.*} (D36, D37, D38). Keys are per panel. The CAPTCHA is the only brute-force defence of the
 * logins (D23), so switching it off ({@code verify-enabled=false}, for local mocks) also needs
 * {@code allow-disabled=true}; without it the application refuses to start.
 */
@ConfigurationProperties("azs.integration.arcaptcha")
public record ArcaptchaProperties(
        String verifyUrl,
        boolean verifyEnabled,
        boolean allowDisabled,
        Keys bank,
        Keys agency,
        Keys customer,
        Duration connectTimeout,
        Duration readTimeout,
        Tls tls) {

    public ArcaptchaProperties {
        if (!verifyEnabled && !allowDisabled) {
            throw new IllegalStateException("azs.integration.arcaptcha.verify-enabled=false switches the login CAPTCHA off; "
                    + "that is for local mocks only and needs azs.integration.arcaptcha.allow-disabled=true");
        }
    }

    public record Keys(String siteKey, String secretKey) {
    }

    public record Tls(boolean skipVerify) {
    }
}
