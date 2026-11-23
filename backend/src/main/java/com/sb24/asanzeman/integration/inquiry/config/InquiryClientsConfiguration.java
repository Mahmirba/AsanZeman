package com.sb24.asanzeman.integration.inquiry.config;

import com.sb24.asanzeman.integration.gateway.GatewayBearerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class InquiryClientsConfiguration {

    @Bean
    InquiryClients inquiryClients(Environment environment, GatewayBearerInterceptor bearer) {
        return new InquiryClients(environment, bearer);
    }
}
