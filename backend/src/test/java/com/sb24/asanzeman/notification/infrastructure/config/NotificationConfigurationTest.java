package com.sb24.asanzeman.notification.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.notification.application.SmsSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NotificationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(
                    org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(NotificationConfiguration.class);

    @Test
    void theDefaultsAreTheDocumentedOnes() {
        runner.run(context -> assertThat(context.getBean(SmsSettings.class)).isEqualTo(new SmsSettings(20,
                Duration.ofMinutes(2), Duration.ofSeconds(30), Duration.ofHours(1), Duration.ofMinutes(15))));
    }

    @Test
    void everyValueIsConfigurable() {
        runner.withPropertyValues("azs.sms.batch-size=5", "azs.sms.lease=1m", "azs.sms.retry-base=10s",
                        "azs.sms.retry-max=30m", "azs.sms.credentials-backoff=1h")
                .run(context -> assertThat(context.getBean(SmsSettings.class)).isEqualTo(new SmsSettings(5,
                        Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofMinutes(30), Duration.ofHours(1))));
    }
}
