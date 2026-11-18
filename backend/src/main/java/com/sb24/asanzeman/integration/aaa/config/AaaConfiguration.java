package com.sb24.asanzeman.integration.aaa.config;

import com.sb24.asanzeman.integration.aaa.AaaClient;
import com.sb24.asanzeman.integration.aaa.AaaWebServiceTemplates;
import com.sb24.asanzeman.integration.http.HttpClients;
import com.sb24.asanzeman.integration.http.TlsSkipReporting;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AaaProperties.class)
public class AaaConfiguration {

    @Bean
    AaaClient aaaClient(AaaProperties p, MeterRegistry meters) {
        boolean skip = p.tls().skipVerify();
        TlsSkipReporting.report("aaa", skip, meters);
        return new AaaClient(
                AaaWebServiceTemplates.create(p.url(), p.username(), p.password(), p.wss().timestamp(),
                        HttpClients.httpClient(p.connectTimeout(), skip), p.readTimeout()),
                p.applicationName(), p.storeName());
    }
}
