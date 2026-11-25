package com.sb24.asanzeman.request.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.application.RegistrationSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RequestConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(RequestConfiguration.class);

    @Test
    void theDuplicateWindowDefaultsToTenSeconds() {
        runner.run(context -> assertThat(context.getBean(RegistrationSettings.class).duplicateWindow())
                .isEqualTo(Duration.ofSeconds(10)));
    }

    @Test
    void theDuplicateWindowIsConfigurable() {
        runner.withPropertyValues("azs.request.duplicate-window=30s")
                .run(context -> assertThat(context.getBean(RegistrationSettings.class).duplicateWindow())
                        .isEqualTo(Duration.ofSeconds(30)));
    }
}
