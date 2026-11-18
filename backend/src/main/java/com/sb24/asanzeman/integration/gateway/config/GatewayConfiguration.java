package com.sb24.asanzeman.integration.gateway.config;

import com.sb24.asanzeman.integration.gateway.GatewayBearerInterceptor;
import com.sb24.asanzeman.integration.gateway.GatewayTokenProvider;
import com.sb24.asanzeman.integration.gateway.GatewayTokenRestClients;
import com.sb24.asanzeman.integration.http.HttpClients;
import com.sb24.asanzeman.integration.http.TlsSkipReporting;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfiguration {

    @Bean
    GatewayTokenProvider gatewayTokenProvider(GatewayProperties p, MeterRegistry meters) {
        boolean skip = p.tls().skipVerify();
        TlsSkipReporting.report("gateway", skip, meters);
        RestClient tokenClient = GatewayTokenRestClients.configure(RestClient.builder()
                .requestFactory(HttpClients.requestFactory(p.connectTimeout(), p.readTimeout(), skip))).build();
        return GatewayTokenProvider.create(p.tokenUrl(), p.clientId(), p.clientSecret(), tokenClient,
                p.refreshBeforeExpiry(), Clock.systemUTC(), p.tokenRetries(), p.retryBackoff(),
                duration -> Thread.sleep(duration));
    }

    @Bean
    GatewayBearerInterceptor gatewayBearerInterceptor(GatewayTokenProvider provider) {
        return new GatewayBearerInterceptor(provider);
    }
}
