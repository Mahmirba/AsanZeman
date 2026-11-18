package com.sb24.asanzeman.integration.personnel.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code azs.integration.personnel-info.*} (6.2, D36, D38). */
@ConfigurationProperties("azs.integration.personnel-info")
public record PersonnelInfoProperties(String url, Duration connectTimeout, Duration readTimeout, Tls tls) {

    public record Tls(boolean skipVerify) {
    }
}
