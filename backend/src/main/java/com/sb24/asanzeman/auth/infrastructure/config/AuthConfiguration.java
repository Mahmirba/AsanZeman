package com.sb24.asanzeman.auth.infrastructure.config;

import com.sb24.asanzeman.auth.application.BankLoginSettings;
import com.sb24.asanzeman.auth.application.TokenSettings;
import com.sb24.asanzeman.auth.domain.port.out.AccessTokenIssuerPort;
import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.auth.infrastructure.security.JwtSupport;
import com.sb24.asanzeman.auth.infrastructure.security.NimbusAccessTokenIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfiguration {

    @Bean
    JwtEncoder jwtEncoder(JwtProperties p) {
        return JwtSupport.encoder(p.privateKeyPem(), p.publicKeyPem());
    }

    @Bean
    AccessTokenIssuerPort accessTokenIssuer(JwtEncoder encoder, JwtProperties p) {
        return new NimbusAccessTokenIssuer(encoder, p.issuer());
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties p, JwtBlacklistPort blacklist) {
        return JwtSupport.decoder(p.publicKeyPem(), p.issuer(), blacklist);
    }

    @Bean
    TokenSettings tokenSettings(JwtProperties p) {
        return new TokenSettings(p.accessTokenTtl(), p.refreshTokenTtl());
    }

    @Bean
    BankLoginSettings bankLoginSettings(@Value("${azs.auth.bank.account-suffix:}") String accountSuffix) {
        return new BankLoginSettings(accountSuffix);
    }
}
