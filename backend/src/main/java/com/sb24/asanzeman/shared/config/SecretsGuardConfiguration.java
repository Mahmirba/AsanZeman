package com.sb24.asanzeman.shared.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

/** Runs {@link SecretsGuard} against every {@code azs.*} and {@code spring.datasource.*} property at startup. */
@Configuration(proxyBeanMethods = false)
public class SecretsGuardConfiguration {

    @Bean
    SecretsGuardRunner secretsGuardRunner(ConfigurableEnvironment environment) {
        Map<String, String> candidates = new HashMap<>();
        for (var source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith("azs.") || name.startsWith("spring.datasource.")) {
                        candidates.putIfAbsent(name, environment.getProperty(name));
                    }
                }
            }
        }
        SecretsGuard.verify(candidates);
        return new SecretsGuardRunner();
    }

    /** Marker bean: its existence proves the guard ran. */
    public static final class SecretsGuardRunner {
    }
}
