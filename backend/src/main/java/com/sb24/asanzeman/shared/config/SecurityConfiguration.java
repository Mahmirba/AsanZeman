package com.sb24.asanzeman.shared.config;

import com.sb24.asanzeman.shared.web.ProblemDetailSecurityHandlers;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

/**
 * D7: stateless, no cookies, no CSRF; every call carries {@code Authorization: Bearer <access token>}, an RS256 JWT
 * whose {@code role} claim becomes the authority {@code ROLE_<role>}. The decoder also rejects tokens revoked at logout.
 * The login and refresh endpoints are public; everything else needs a token, and 401/403 are Problem Details (D48).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    static final String[] PUBLIC = {
            "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/api/auth/*/login", "/api/auth/customer/otp/verify", "/api/auth/refresh"
    };

    static final String ROLE_CLAIM = "role";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandlers handlers,
            JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .headers(h -> h
                        // the panels are served over HTTPS only, so HSTS is always sent, also behind a TLS proxy
                        .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31_536_000)
                                .requestMatcher(AnyRequestMatcher.INSTANCE))
                        // an API answers JSON, so no page content may load from it and nobody may frame it
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(ROLE_CLAIM);
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }
}
