package com.sb24.asanzeman.integration.personnel.config;

import com.sb24.asanzeman.integration.gateway.GatewayBearerInterceptor;
import com.sb24.asanzeman.integration.http.HttpClients;
import com.sb24.asanzeman.integration.http.TlsSkipReporting;
import com.sb24.asanzeman.integration.personnel.PersonnelInfoClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PersonnelInfoProperties.class)
public class PersonnelInfoConfiguration {

    @Bean
    PersonnelInfoClient personnelInfoClient(PersonnelInfoProperties p, GatewayBearerInterceptor gatewayBearer,
            MeterRegistry meters) {
        TlsSkipReporting.report("personnel-info", p.tls().skipVerify(), meters);
        RestClient restClient = RestClient.builder()
                .requestFactory(HttpClients.requestFactory(p.connectTimeout(), p.readTimeout(),
                        p.tls().skipVerify()))
                .requestInterceptor(gatewayBearer).build();
        return new PersonnelInfoClient(restClient, p.url());
    }
}
