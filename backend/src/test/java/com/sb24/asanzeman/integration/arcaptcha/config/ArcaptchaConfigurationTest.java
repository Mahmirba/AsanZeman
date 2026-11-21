package com.sb24.asanzeman.integration.arcaptcha.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.integration.arcaptcha.ArcaptchaClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The wiring is excluded from the coverage gate, so this proves the bean comes up from the properties. */
class ArcaptchaConfigurationTest {

    @Test
    void createsTheClientAndBindsTheKeysOfEachPanel() {
        new ApplicationContextRunner().withUserConfiguration(ArcaptchaConfiguration.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues(
                        "azs.integration.arcaptcha.verify-url=https://captcha.test/verify",
                        "azs.integration.arcaptcha.verify-enabled=true",
                        "azs.integration.arcaptcha.bank.site-key=bs",
                        "azs.integration.arcaptcha.bank.secret-key=bk",
                        "azs.integration.arcaptcha.agency.site-key=as",
                        "azs.integration.arcaptcha.agency.secret-key=ak",
                        "azs.integration.arcaptcha.customer.site-key=cs",
                        "azs.integration.arcaptcha.customer.secret-key=ck",
                        "azs.integration.arcaptcha.connect-timeout=2s",
                        "azs.integration.arcaptcha.read-timeout=5s",
                        "azs.integration.arcaptcha.tls.skip-verify=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ArcaptchaClient.class);
                    ArcaptchaProperties p = context.getBean(ArcaptchaProperties.class);
                    assertThat(p.bank().siteKey()).isEqualTo("bs");
                    assertThat(p.agency().secretKey()).isEqualTo("ak");
                    assertThat(p.customer().siteKey()).isEqualTo("cs");
                    assertThat(context.getBean(MeterRegistry.class).get("azs.tls.skip.verify")
                            .tag("service", "arcaptcha").gauge().value()).isEqualTo(1.0);
                });
    }
}
