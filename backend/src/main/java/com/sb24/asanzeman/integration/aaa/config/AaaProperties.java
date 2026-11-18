package com.sb24.asanzeman.integration.aaa.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code azs.integration.aaa.*} (6.2, D39). {@code username} and {@code password} are the AAA <em>technical account</em>
 * (secrets, D13). {@code roleMap} maps an AAA role name to an Asan Zeman role, for example {@code Branch -> BRANCH}.
 */
@ConfigurationProperties("azs.integration.aaa")
public record AaaProperties(
        String url,
        String applicationName,
        String storeName,
        String username,
        String password,
        Map<String, String> roleMap,
        Wss wss,
        Duration connectTimeout,
        Duration readTimeout,
        Tls tls) {

    public record Wss(boolean timestamp) {
    }

    public record Tls(boolean skipVerify) {
    }
}
