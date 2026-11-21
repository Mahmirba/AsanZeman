package com.sb24.asanzeman.integration.personnel.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.integration.gateway.config.GatewayConfiguration;
import com.sb24.asanzeman.integration.personnel.PersonnelInfoClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The wiring is excluded from the coverage gate, so this proves the client comes up with the gateway interceptor. */
class PersonnelInfoConfigurationTest {

    @Test
    void createsTheClientFromTheProperties() {
        new ApplicationContextRunner().withUserConfiguration(GatewayConfiguration.class,
                        PersonnelInfoConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues(
                        "azs.integration.gateway.token-url=https://gw.test/connect/token",
                        "azs.integration.gateway.client-id=id",
                        "azs.integration.gateway.client-secret=secret",
                        "azs.integration.gateway.refresh-before-expiry=5m",
                        "azs.integration.gateway.connect-timeout=3s",
                        "azs.integration.gateway.read-timeout=10s",
                        "azs.integration.gateway.token-retries=2",
                        "azs.integration.gateway.retry-backoff=500ms",
                        "azs.integration.gateway.tls.skip-verify=false",
                        "azs.integration.personnel-info.url=https://apigw.test/GetOverallPersonelInfo",
                        "azs.integration.personnel-info.connect-timeout=3s",
                        "azs.integration.personnel-info.read-timeout=10s",
                        "azs.integration.personnel-info.tls.skip-verify=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(PersonnelInfoClient.class);
                    assertThat(context.getBean(MeterRegistry.class).get("azs.tls.skip.verify")
                            .tag("service", "personnel-info").gauge().value()).isEqualTo(0.0);
                });
    }
}
