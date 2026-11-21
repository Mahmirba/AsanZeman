package com.sb24.asanzeman.request.infrastructure.config;

import com.sb24.asanzeman.request.application.RegistrationSettings;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RequestConfiguration {

    @Bean
    RegistrationSettings registrationSettings(@Value("${azs.request.duplicate-window:10s}") Duration duplicateWindow) {
        return new RegistrationSettings(duplicateWindow);
    }
}
