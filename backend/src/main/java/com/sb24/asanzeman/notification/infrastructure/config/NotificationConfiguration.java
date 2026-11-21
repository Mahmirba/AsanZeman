package com.sb24.asanzeman.notification.infrastructure.config;

import com.sb24.asanzeman.notification.application.SmsSettings;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NotificationConfiguration {

    @Bean
    SmsSettings smsSettings(@Value("${azs.sms.batch-size:20}") int batchSize,
            @Value("${azs.sms.lease:2m}") Duration lease, @Value("${azs.sms.retry-base:30s}") Duration retryBase,
            @Value("${azs.sms.retry-max:1h}") Duration retryMax,
            @Value("${azs.sms.credentials-backoff:15m}") Duration credentialsBackoff) {
        return new SmsSettings(batchSize, lease, retryBase, retryMax, credentialsBackoff);
    }
}
