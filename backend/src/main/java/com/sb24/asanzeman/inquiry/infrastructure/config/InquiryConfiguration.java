package com.sb24.asanzeman.inquiry.infrastructure.config;

import com.sb24.asanzeman.inquiry.application.InquirySettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InquiryProperties.class)
public class InquiryConfiguration {

    @Bean
    InquirySettings inquirySettings(InquiryProperties p) {
        return InquirySettings.parse(p.stages(), p.stageDeadlines());
    }
}
