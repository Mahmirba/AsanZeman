package com.sb24.asanzeman.integration.yaghut.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutSessionManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The wiring is excluded from the coverage gate, so this proves the beans come up from the properties. */
class YaghutConfigurationTest {

    @Test
    void createsTheClientAndTheSessionManager() {
        new ApplicationContextRunner().withUserConfiguration(YaghutConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues(
                        "azs.integration.yaghut.base-url=https://yaghut.test",
                        "azs.integration.yaghut.technical-user.username=svc",
                        "azs.integration.yaghut.technical-user.password=pw",
                        "azs.integration.yaghut.connect-timeout=3s",
                        "azs.integration.yaghut.read-timeout=10s",
                        "azs.integration.yaghut.tls.skip-verify=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(YaghutClient.class);
                    assertThat(context).hasSingleBean(YaghutSessionManager.class);
                    assertThat(context.getBean(MeterRegistry.class).get("azs.tls.skip.verify")
                            .tag("service", "yaghut").gauge().value()).isEqualTo(0.0);
                });
    }
}
