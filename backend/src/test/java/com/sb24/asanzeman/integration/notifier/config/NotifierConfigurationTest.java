package com.sb24.asanzeman.integration.notifier.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.integration.notifier.NotifierClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class NotifierConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class Meters {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Meters.class, NotifierConfiguration.class)
            .withPropertyValues("azs.integration.notifier.base-url=https://notifier.test",
                    "azs.integration.notifier.user=u", "azs.integration.notifier.pass=p",
                    "azs.integration.notifier.client-name=AZS");

    @Test
    void buildsTheClientAndBindsTheSettings() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(NotifierClient.class);
            NotifierProperties p = context.getBean(NotifierProperties.class);
            assertThat(p.baseUrl()).isEqualTo("https://notifier.test");
            assertThat(p.clientName()).isEqualTo("AZS");
        });
    }

    @Test
    void missingTimeoutsOtpArgsAndTlsGetDefaults() {
        var p = new NotifierProperties("u", "user", "pass", "AZS", null, null, null, null);

        assertThat(p.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(p.readTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(p.otpArgs()).isEmpty();
        assertThat(p.tls().skipVerify()).isFalse();
    }

    @Test
    void givenValuesAreKept() {
        var p = new NotifierProperties("u", "user", "pass", "AZS", List.of("x"), Duration.ofSeconds(1),
                Duration.ofSeconds(2), new NotifierProperties.Tls(true));

        assertThat(p.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(p.readTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(p.otpArgs()).containsExactly("x");
        assertThat(p.tls().skipVerify()).isTrue();
    }
}
