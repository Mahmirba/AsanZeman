package com.sb24.asanzeman.shared.config;

import com.sb24.asanzeman.shared.web.ProblemDetailSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * D7: stateless, no cookies, no CSRF; every call carries {@code Authorization: Bearer}. The JWT
 * decoder and the login endpoints arrive with the auth module (Phase 1); until then every
 * non-public path answers 401 with a Problem Details body.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    static final String[] PUBLIC = {
            "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/api/auth/*/login", "/api/auth/customer/otp/verify", "/api/auth/refresh"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandlers handlers) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .headers(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .build();
    }
}
