package com.sb24.asanzeman.scheduler.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SchedulerConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    static class Data {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:sched;MODE=Oracle", "sa", "");
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Data.class, SchedulerConfiguration.class);

    @Test
    void jobsAreOnByDefaultWithALockProvider() {
        runner.run(context -> assertThat(context).hasSingleBean(LockProvider.class));
    }

    @Test
    void jobsCanBeSwitchedOff() {
        runner.withPropertyValues("azs.scheduler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LockProvider.class));
    }
}
