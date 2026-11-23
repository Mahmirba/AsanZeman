package com.sb24.asanzeman.request.infrastructure.config;

import com.sb24.asanzeman.request.application.CompletionSettings;
import com.sb24.asanzeman.request.application.RegistrationSettings;
import com.sb24.asanzeman.request.application.ReviewSettings;
import com.sb24.asanzeman.request.domain.model.CollateralPolicy;
import com.sb24.asanzeman.request.domain.model.DepositRules;
import com.sb24.asanzeman.request.domain.service.CollateralCalculator;
import com.sb24.asanzeman.request.domain.service.DepositEligibility;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CollateralProperties.class)
public class RequestConfiguration {

    @Bean
    RegistrationSettings registrationSettings(@Value("${azs.request.duplicate-window:10s}") Duration duplicateWindow) {
        return new RegistrationSettings(duplicateWindow);
    }

    @Bean
    CompletionSettings completionSettings(@Value("${azs.request.completion-window:7d}") Duration window) {
        return new CompletionSettings(window);
    }

    @Bean
    ReviewSettings reviewSettings(@Value("${azs.lock.review-idle-timeout:60m}") Duration idleTimeout) {
        return new ReviewSettings(idleTimeout);
    }

    @Bean
    CollateralCalculator collateralCalculator(CollateralProperties p) {
        return new CollateralCalculator(new CollateralPolicy(p.cashMinPercent(), p.shareStepPercent(),
                p.cheapFactor(), p.expensiveFactor(), p.expensiveMaxBalancePercent()));
    }

    @Bean
    DepositEligibility depositEligibility(CollateralProperties p) {
        return new DepositEligibility(new DepositRules(p.rialCurrency(), Set.copyOf(p.individualOwners()), p.groups(),
                p.typeCodes()));
    }
}
