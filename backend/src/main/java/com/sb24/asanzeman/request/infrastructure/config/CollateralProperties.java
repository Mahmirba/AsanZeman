package com.sb24.asanzeman.request.infrastructure.config;

import com.sb24.asanzeman.request.domain.model.PriceClass;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code azs.collateral.*} (D14, D15, D18, 4.3): the share rules and pledge factors, and what decides which deposits
 * may be offered and how they are priced: the rial currency code, the {@code owner} values that mean "held alone",
 * the price class of each Yaghut group, and the numeric type codes that decide a class on their own (the Win deposits).
 */
@ConfigurationProperties("azs.collateral")
public record CollateralProperties(Integer cashMinPercent, Integer shareStepPercent, BigDecimal cheapFactor,
        BigDecimal expensiveFactor, Integer expensiveMaxBalancePercent, String rialCurrency,
        List<String> individualOwners, Map<String, PriceClass> groups, Map<Long, PriceClass> typeCodes) {

    public CollateralProperties {
        cashMinPercent = cashMinPercent == null ? 20 : cashMinPercent;
        shareStepPercent = shareStepPercent == null ? 10 : shareStepPercent;
        cheapFactor = cheapFactor == null ? new BigDecimal("1.00") : cheapFactor;
        expensiveFactor = expensiveFactor == null ? new BigDecimal("1.05") : expensiveFactor;
        expensiveMaxBalancePercent = expensiveMaxBalancePercent == null ? 95 : expensiveMaxBalancePercent;
        rialCurrency = rialCurrency == null ? "IRR" : rialCurrency;
        individualOwners = individualOwners == null ? List.of("INDIVIDUAL") : List.copyOf(individualOwners);
        groups = groups == null ? Map.of() : Map.copyOf(groups);
        typeCodes = typeCodes == null ? Map.of() : Map.copyOf(typeCodes);
    }
}
