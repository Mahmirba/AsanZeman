package com.sb24.asanzeman.request.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.PriceClass;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The configuration keys bind as the file writes them, and anything missing has the SRS default (4.3). */
class CollateralPropertiesTest {

    @Test
    void theKeysOfTheConfigurationFileBind() {
        var source = new MapConfigurationPropertySource(Map.ofEntries(
                Map.entry("azs.collateral.cash-min-percent", "30"), Map.entry("azs.collateral.share-step-percent", "5"),
                Map.entry("azs.collateral.cheap-factor", "1.00"), Map.entry("azs.collateral.expensive-factor", "1.10"),
                Map.entry("azs.collateral.expensive-max-balance-percent", "90"),
                Map.entry("azs.collateral.rial-currency", "RIAL"),
                Map.entry("azs.collateral.individual-owners", "SINGLE,SOLE"),
                Map.entry("azs.collateral.groups.SHORT_ACCOUNT", "CHEAP"),
                Map.entry("azs.collateral.groups.LONG_ACCOUNT", "EXPENSIVE"),
                Map.entry("azs.collateral.type-codes.77", "CHEAP")));

        var p = new Binder(source).bind("azs.collateral", CollateralProperties.class).get();

        assertThat(p.cashMinPercent()).isEqualTo(30);
        assertThat(p.shareStepPercent()).isEqualTo(5);
        assertThat(p.expensiveFactor()).isEqualByComparingTo("1.10");
        assertThat(p.expensiveMaxBalancePercent()).isEqualTo(90);
        assertThat(p.rialCurrency()).isEqualTo("RIAL");
        assertThat(p.individualOwners()).containsExactly("SINGLE", "SOLE");
        assertThat(p.groups()).containsEntry("SHORT_ACCOUNT", PriceClass.CHEAP)
                .containsEntry("LONG_ACCOUNT", PriceClass.EXPENSIVE);
        assertThat(p.typeCodes()).containsEntry(77L, PriceClass.CHEAP);
    }

    @Test
    void whatIsNotConfiguredHasTheSrsDefault() {
        var p = new CollateralProperties(null, null, null, null, null, null, null, null, null);

        assertThat(p.cashMinPercent()).isEqualTo(20);
        assertThat(p.shareStepPercent()).isEqualTo(10);
        assertThat(p.cheapFactor()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(p.expensiveFactor()).isEqualByComparingTo(new BigDecimal("1.05"));
        assertThat(p.expensiveMaxBalancePercent()).isEqualTo(95);
        assertThat(p.rialCurrency()).isEqualTo("IRR");
        assertThat(p.individualOwners()).isEqualTo(List.of("INDIVIDUAL"));
        assertThat(p.groups()).isEmpty();
        assertThat(p.typeCodes()).isEmpty();
    }
}
