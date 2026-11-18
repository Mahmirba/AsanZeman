package com.sb24.asanzeman.integration.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code azs.integration.gateway.*} (D28, D36, D38). */
@ConfigurationProperties("azs.integration.gateway")
public record GatewayProperties(
        String tokenUrl,
        String clientId,
        String clientSecret,
        Duration refreshBeforeExpiry,
        Duration connectTimeout,
        Duration readTimeout,
        int tokenRetries,
        Duration retryBackoff,
        Tls tls) {

    public record Tls(boolean skipVerify) {
    }
}
