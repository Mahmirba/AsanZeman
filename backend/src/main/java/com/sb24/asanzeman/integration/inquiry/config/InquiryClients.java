package com.sb24.asanzeman.integration.inquiry.config;

import com.sb24.asanzeman.integration.gateway.GatewayBearerInterceptor;
import com.sb24.asanzeman.integration.http.HttpClients;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryEndpoint;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * Builds the client of one inquiry from {@code azs.integration.inquiry.<name>.*} (5.3): its own timeout, retries and
 * fixed fields, the shared host and TLS setting, and the gateway bearer token. A missing {@code path} stops the startup.
 */
public class InquiryClients {

    private static final String PREFIX = "azs.integration.inquiry";

    private final Binder binder;
    private final GatewayBearerInterceptor bearer;
    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration retryBackoff;
    private final boolean skipTlsVerify;

    public InquiryClients(Environment environment, GatewayBearerInterceptor bearer) {
        this.binder = Binder.get(environment);
        this.bearer = bearer;
        this.baseUrl = binder.bind(PREFIX + ".base-url", String.class).orElse("");
        this.connectTimeout = binder.bind(PREFIX + ".connect-timeout", Duration.class).orElse(Duration.ofSeconds(3));
        this.retryBackoff = binder.bind(PREFIX + ".retry-backoff", Duration.class).orElse(Duration.ofMillis(200));
        this.skipTlsVerify = binder.bind(PREFIX + ".tls.skip-verify", Boolean.class).orElse(false);
    }

    public InquiryClient create(String name) {
        InquiryEndpoint endpoint = binder.bind(PREFIX + "." + name, InquiryEndpoint.class)
                .orElseThrow(() -> new IllegalStateException(PREFIX + "." + name + " is not set"));
        if (endpoint.path() == null || endpoint.path().isBlank()) {
            throw new IllegalStateException(PREFIX + "." + name + ".path is not set");
        }
        String host = endpoint.baseUrl() == null || endpoint.baseUrl().isBlank() ? baseUrl : endpoint.baseUrl();
        RestClient rest = RestClient.builder()
                .requestFactory(HttpClients.requestFactory(connectTimeout, endpoint.timeout(), skipTlsVerify))
                .requestInterceptor(bearer).build();
        return new InquiryClient(rest, host, endpoint, retryBackoff);
    }
}
