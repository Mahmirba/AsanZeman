package com.sb24.asanzeman.integration.yaghut.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code azs.integration.yaghut.*} (D35, D36, D38). The technical account's credentials are secrets (D13). */
@ConfigurationProperties("azs.integration.yaghut")
public record YaghutProperties(
        String baseUrl,
        TechnicalUser technicalUser,
        Duration connectTimeout,
        Duration readTimeout,
        Tls tls) {

    public record TechnicalUser(String username, String password) {
    }

    public record Tls(boolean skipVerify) {
    }
}
