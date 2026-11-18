package com.sb24.asanzeman.integration.arcaptcha.config;

import com.sb24.asanzeman.integration.arcaptcha.ArcaptchaClient;
import com.sb24.asanzeman.integration.http.HttpClients;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArcaptchaProperties.class)
public class ArcaptchaConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ArcaptchaConfiguration.class);

    @Bean
    ArcaptchaClient arcaptchaClient(ArcaptchaProperties properties, MeterRegistry meters) {
        boolean skip = properties.tls().skipVerify();
        if (skip) {
            log.warn("TLS certificate checking is OFF for ARcaptcha (azs.integration.arcaptcha.tls.skip-verify=true)");
        }
        Gauge.builder("azs.tls.skip.verify", () -> skip ? 1 : 0).tag("service", "arcaptcha").register(meters);
        return new ArcaptchaClient(
                HttpClients.restClient(properties.connectTimeout(), properties.readTimeout(), skip),
                properties.verifyUrl());
    }
}
