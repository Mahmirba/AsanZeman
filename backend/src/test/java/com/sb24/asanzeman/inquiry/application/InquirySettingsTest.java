package com.sb24.asanzeman.inquiry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.infrastructure.config.InquiryProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class InquirySettingsTest {

    @Test
    void readsStagesSeparatedBySemicolonAndInquiriesSeparatedByComma() {
        var settings = InquirySettings.parse("SABT_AHVAL; SHAHKAR , SEPAD",
                List.of(Duration.ofSeconds(10), Duration.ofSeconds(15)));

        assertThat(settings.stages()).containsExactly(List.of(InquiryType.SABT_AHVAL),
                List.of(InquiryType.SHAHKAR, InquiryType.SEPAD));
        assertThat(settings.deadlines()).containsExactly(Duration.ofSeconds(10), Duration.ofSeconds(15));
    }

    @Test
    void anUnknownInquiryNameStopsTheStartupNamingIt() {
        assertThatThrownBy(() -> InquirySettings.parse("SABT_AHVAL;SHAKAR", List.of(Duration.ZERO, Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SHAKAR");
    }

    @Test
    void theConfigurationFileKeysBuildTheSettingsAndTheRunLifecycleWithItsDefaults() {
        var source = new MapConfigurationPropertySource(Map.of(
                "azs.inquiry.stages", "SABT_AHVAL;SHAHKAR,SEPAD,BLACKLIST,SAMA_CHEQUE,SAMAT,BOARD_RELATION,IMPORT_COMMITMENT,EXPORT_COMMITMENT",
                "azs.inquiry.stage-deadlines", "10s,15s", "azs.inquiry.sepad.min-age", "18"));

        InquiryProperties p = new Binder(source).bind("azs.inquiry", InquiryProperties.class).get();
        var settings = InquirySettings.parse(p.stages(), p.stageDeadlines());

        assertThat(settings.stages()).hasSize(2);
        assertThat(settings.stages().get(1)).hasSize(8);
        assertThat(settings.deadlines()).containsExactly(Duration.ofSeconds(10), Duration.ofSeconds(15));
        assertThat(p.staleRunAfter()).isEqualTo(Duration.ofSeconds(60));
        assertThat(p.customerRetryCooldown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(p.passedResultReuse()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void theRunLifecycleCanBeConfigured() {
        var source = new MapConfigurationPropertySource(Map.of("azs.inquiry.stages", "SABT_AHVAL",
                "azs.inquiry.stage-deadlines", "10s", "azs.inquiry.stale-run-after", "2m",
                "azs.inquiry.customer-retry-cooldown", "30s", "azs.inquiry.passed-result-reuse", "30m"));

        InquiryProperties p = new Binder(source).bind("azs.inquiry", InquiryProperties.class).get();

        assertThat(p.staleRunAfter()).isEqualTo(Duration.ofMinutes(2));
        assertThat(p.customerRetryCooldown()).isEqualTo(Duration.ofSeconds(30));
        assertThat(p.passedResultReuse()).isEqualTo(Duration.ofMinutes(30));
    }
}
