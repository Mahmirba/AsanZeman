package com.sb24.asanzeman.integration.notifier.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code azs.integration.notifier.*} (D34, D36, D38, D40): the SMS centre, called directly without a gateway token. */
@ConfigurationProperties("azs.integration.notifier")
public record NotifierProperties(
        String baseUrl,
        String user,
        String pass,
        String clientName,
        List<String> otpArgs,
        Duration connectTimeout,
        Duration readTimeout,
        Tls tls) {

    public NotifierProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
        otpArgs = otpArgs == null ? List.of() : List.copyOf(otpArgs);
        tls = tls == null ? new Tls(false) : tls;
    }

    public record Tls(boolean skipVerify) {
    }
}
