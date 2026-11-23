package com.sb24.asanzeman.inquiry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.infrastructure.config.InquiryConfiguration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
    void theConfigurationFileKeysBuildTheSettings() {
        new ApplicationContextRunner().withUserConfiguration(InquiryConfiguration.class)
                .withPropertyValues(
                        "azs.inquiry.stages=SABT_AHVAL;SHAHKAR,SEPAD,BLACKLIST,SAMA_CHEQUE,SAMAT,BOARD_RELATION,IMPORT_COMMITMENT,EXPORT_COMMITMENT",
                        "azs.inquiry.stage-deadlines=10s,15s", "azs.inquiry.sepad.min-age=18")
                .run(context -> {
                    var settings = context.getBean(InquirySettings.class);
                    assertThat(settings.stages()).hasSize(2);
                    assertThat(settings.stages().get(1)).hasSize(8);
                    assertThat(settings.deadlines()).containsExactly(Duration.ofSeconds(10), Duration.ofSeconds(15));
                });
    }
}
