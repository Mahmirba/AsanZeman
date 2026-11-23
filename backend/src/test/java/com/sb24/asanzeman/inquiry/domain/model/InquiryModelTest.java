package com.sb24.asanzeman.inquiry.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InquiryModelTest {

    private static final CivilData CIVIL = new CivilData(Gender.MALE, LocalDate.of(1990, 5, 17));

    @Test
    void theFourKindsOfOutcomeCarryWhatTheyNeed() {
        assertThat(InquiryOutcome.passed(InquiryType.SHAHKAR).result()).isEqualTo(InquiryResult.PASSED);
        assertThat(InquiryOutcome.passed(InquiryType.SABT_AHVAL, CIVIL).civil()).isEqualTo(CIVIL);
        var negative = InquiryOutcome.negative(InquiryType.BLACKLIST, "inquiry.blacklist.negative");
        assertThat(negative.result()).isEqualTo(InquiryResult.NEGATIVE);
        assertThat(negative.messageKey()).isEqualTo("inquiry.blacklist.negative");
        var failed = InquiryOutcome.failed(InquiryType.SAMAT, FailureKind.TIMEOUT);
        assertThat(failed.result()).isEqualTo(InquiryResult.FAILED);
        assertThat(failed.failure()).isEqualTo(FailureKind.TIMEOUT);
        assertThat(InquiryOutcome.skipped(InquiryType.SEPAD).result()).isEqualTo(InquiryResult.SKIPPED);
    }

    @Test
    void anOutcomeNeedsItsTypeAndResult() {
        assertThatThrownBy(() -> new InquiryOutcome(null, InquiryResult.PASSED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InquiryOutcome(InquiryType.SAMAT, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNegativeNeedsTheMessageToShowAndAFailedNeedsItsKind() {
        assertThatThrownBy(() -> InquiryOutcome.negative(InquiryType.SAMAT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InquiryOutcome.negative(InquiryType.SAMAT, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InquiryOutcome.failed(InquiryType.SAMAT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void civilDataNeedsBothGenderAndBirthDate() {
        assertThatThrownBy(() -> new CivilData(null, LocalDate.of(1990, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CivilData(Gender.FEMALE, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theContextGainsTheCivilDataAndKnowsTheAgeInWholeYears() {
        var context = new InquiryContext("1000000011", "09121234567", 1000, "0123");
        assertThat(context.civil()).isNull();

        var withCivil = context.withCivil(CIVIL);

        assertThat(context.civil()).isNull();
        assertThat(withCivil.civil()).isEqualTo(CIVIL);
        assertThat(withCivil.ageOn(LocalDate.of(2026, 5, 16))).isEqualTo(35);
        assertThat(withCivil.ageOn(LocalDate.of(2026, 5, 17))).isEqualTo(36);
    }

    @Test
    void aVerdictKeepsItsOwnCopyOfTheOutcomes() {
        var outcomes = new ArrayList<>(List.of(InquiryOutcome.passed(InquiryType.SHAHKAR)));
        var verdict = new InquiryVerdict(InquiryVerdict.Decision.PROCEED, null, outcomes);

        outcomes.clear();

        assertThat(verdict.outcomes()).hasSize(1);
    }
}
