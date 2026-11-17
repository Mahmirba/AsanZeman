package com.sb24.asanzeman.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class IranianNationalCodeValidatorTest {

    record Form(@IranianNationalCode String nationalCode) {
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidNormalisedAndPersianDigitCodes() {
        assertThat(validator.validate(new Form("1000000011"))).isEmpty();
        assertThat(validator.validate(new Form("۱۰۰۰۰۰۰۰۱۱"))).isEmpty();
    }

    @Test
    void leavesNullToNotNull() {
        assertThat(validator.validate(new Form(null))).isEmpty();
        assertThat(new IranianNationalCodeValidator().isValid(null, null)).isTrue();
    }

    @Test
    void rejectsInvalidCodesWithTheConfiguredMessageKey() {
        var violations = validator.validate(new Form("1000000012"));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessageTemplate()).isEqualTo("{azs.validation.national-code}");
    }
}
