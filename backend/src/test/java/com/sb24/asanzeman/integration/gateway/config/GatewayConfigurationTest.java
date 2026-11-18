package com.sb24.asanzeman.integration.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.integration.gateway.GatewayBearerInterceptor;
import com.sb24.asanzeman.integration.gateway.GatewayTokenProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The wiring is excluded from the coverage gate, so this proves the beans really come up from the properties. */
class GatewayConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayConfiguration.class)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    private static String[] properties(boolean skipVerify) {
        return new String[] {
                "azs.integration.gateway.token-url=https://gw.test/connect/token",
                "azs.integration.gateway.client-id=id",
                "azs.integration.gateway.client-secret=secret",
                "azs.integration.gateway.refresh-before-expiry=5m",
                "azs.integration.gateway.connect-timeout=3s",
                "azs.integration.gateway.read-timeout=10s",
                "azs.integration.gateway.token-retries=2",
                "azs.integration.gateway.retry-backoff=500ms",
                "azs.integration.gateway.tls.skip-verify=" + skipVerify};
    }

    @Test
    void createsTheTokenProviderAndTheInterceptor() {
        runner.withPropertyValues(properties(false)).run(context -> {
            assertThat(context).hasSingleBean(GatewayTokenProvider.class);
            assertThat(context).hasSingleBean(GatewayBearerInterceptor.class);
            assertThat(context.getBean(MeterRegistry.class).get("azs.tls.skip.verify").tag("service", "gateway")
                    .gauge().value()).isEqualTo(0.0);
        });
    }

    @Test
    void reportsWhenCertificateCheckingIsOff() {
        runner.withPropertyValues(properties(true)).run(context -> assertThat(
                context.getBean(MeterRegistry.class).get("azs.tls.skip.verify").tag("service", "gateway").gauge()
                        .value()).isEqualTo(1.0));
    }
}
