package com.sb24.asanzeman.integration.notifier.config;

import com.sb24.asanzeman.integration.http.HttpClients;
import com.sb24.asanzeman.integration.http.TlsSkipReporting;
import com.sb24.asanzeman.integration.notifier.NotifierClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotifierProperties.class)
public class NotifierConfiguration {

    @Bean
    NotifierClient notifierClient(NotifierProperties p, MeterRegistry meters) {
        TlsSkipReporting.report("notifier", p.tls().skipVerify(), meters);
        return new NotifierClient(HttpClients.restClient(p.connectTimeout(), p.readTimeout(), p.tls().skipVerify()),
                p.baseUrl(), p.user(), p.pass(), p.clientName());
    }
}
