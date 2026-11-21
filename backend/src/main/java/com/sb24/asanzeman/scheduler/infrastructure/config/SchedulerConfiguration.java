package com.sb24.asanzeman.scheduler.infrastructure.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Leader election (3-3-7, D41): one instance runs each job at a time, through ShedLock on
 * {@code AZS_BUSINESS.scheduler_lock} in the single datasource. {@code azs.scheduler.enabled=false} turns every job
 * off (tests, and a node that must not run jobs).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "azs.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerConfiguration {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("azs_business.scheduler_lock")
                .usingDbTime()
                .build());
    }
}
