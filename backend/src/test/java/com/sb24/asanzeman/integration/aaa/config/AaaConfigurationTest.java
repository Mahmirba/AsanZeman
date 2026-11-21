package com.sb24.asanzeman.integration.aaa.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.integration.aaa.AaaClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The wiring is excluded from the coverage gate, so this proves the client and the role map come up. */
class AaaConfigurationTest {

    @Test
    void createsTheClientAndBindsTheRoleMapWithItsCase() {
        new ApplicationContextRunner().withUserConfiguration(AaaConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues(
                        "azs.integration.aaa.url=https://aaa.test/AuthorizationSrv.svc",
                        "azs.integration.aaa.application-name=app",
                        "azs.integration.aaa.store-name=store",
                        "azs.integration.aaa.username=tech",
                        "azs.integration.aaa.password=pw",
                        "azs.integration.aaa.role-map.Branch=BRANCH",
                        "azs.integration.aaa.role-map.Admin=ADMIN",
                        "azs.integration.aaa.wss.timestamp=false",
                        "azs.integration.aaa.connect-timeout=3s",
                        "azs.integration.aaa.read-timeout=10s",
                        "azs.integration.aaa.tls.skip-verify=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AaaClient.class);
                    assertThat(context.getBean(AaaProperties.class).roleMap())
                            .containsEntry("Branch", "BRANCH").containsEntry("Admin", "ADMIN");
                    assertThat(context.getBean(MeterRegistry.class).get("azs.tls.skip.verify")
                            .tag("service", "aaa").gauge().value()).isEqualTo(0.0);
                });
    }
}
