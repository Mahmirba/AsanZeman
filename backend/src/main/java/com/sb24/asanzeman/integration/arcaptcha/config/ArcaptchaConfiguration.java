package com.sb24.asanzeman.integration.arcaptcha.config;

import com.sb24.asanzeman.integration.arcaptcha.ArcaptchaClient;
import com.sb24.asanzeman.integration.http.HttpClients;
import com.sb24.asanzeman.integration.http.TlsSkipReporting;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ArcaptchaProperties.class)
public class ArcaptchaConfiguration {

    @Bean
    ArcaptchaClient arcaptchaClient(ArcaptchaProperties properties, MeterRegistry meters) {
        boolean skip = properties.tls().skipVerify();
        TlsSkipReporting.report("arcaptcha", skip, meters);
        return new ArcaptchaClient(
                HttpClients.restClient(properties.connectTimeout(), properties.readTimeout(), skip),
                properties.verifyUrl());
    }
}
