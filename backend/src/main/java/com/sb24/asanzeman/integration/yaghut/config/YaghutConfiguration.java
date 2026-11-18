package com.sb24.asanzeman.integration.yaghut.config;

import com.sb24.asanzeman.integration.http.HttpClients;
import com.sb24.asanzeman.integration.http.TlsSkipReporting;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutSessionManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YaghutProperties.class)
public class YaghutConfiguration {

    @Bean
    YaghutClient yaghutClient(YaghutProperties p, MeterRegistry meters) {
        TlsSkipReporting.report("yaghut", p.tls().skipVerify(), meters);
        return new YaghutClient(
                HttpClients.restClient(p.connectTimeout(), p.readTimeout(), p.tls().skipVerify()), p.baseUrl());
    }

    @Bean
    YaghutSessionManager yaghutSessionManager(YaghutClient client, YaghutProperties p) {
        return new YaghutSessionManager(client, p.technicalUser().username(), p.technicalUser().password(),
                Clock.systemUTC());
    }
}
