package com.sb24.asanzeman.shared.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NationalCodeTest {

    // the synthetic test personas from plan.md all carry valid check digits
    @ParameterizedTest
    @ValueSource(strings = {"1000000011", "1000000028", "1000000036", "1000000044", "1000000125", "0012345679"})
    void acceptsValidCodes(String code) {
        assertThat(NationalCode.isValid(code)).isTrue();
        assertThat(NationalCode.parse(code)).contains(new NationalCode(code));
    }

    @Test
    @DisplayName("covers both check-digit branches: remainder below 2 and remainder of 2 or more")
    void coversBothChecksumBranches() {
        assertThat(NationalCode.isValid("0010000070")).isTrue(); // remainder 0 -> check digit 0
        assertThat(NationalCode.isValid("0010000021")).isTrue(); // remainder 1 -> check digit 1
        assertThat(NationalCode.isValid("0010000071")).isFalse();
        assertThat(NationalCode.isValid("1000000011")).isTrue(); // remainder >= 2 -> 11 - remainder
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000012", "1000000029", "10000000-1", "123456789", "12345678901", "12345678a1", "0000000000", "1111111111", "9999999999"})
    void rejectsWrongCheckDigitLengthCharactersAndRepeatedDigits(String code) {
        assertThat(NationalCode.isValid(code)).isFalse();
        assertThat(NationalCode.parse(code)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsNullAndEmpty(String code) {
        assertThat(NationalCode.isValid(code)).isFalse();
        assertThat(NationalCode.parse(code)).isEmpty();
    }

    @Test
    @DisplayName("normalises Persian and Arabic-Indic digits and trims whitespace")
    void normalisesDigits() {
        assertThat(NationalCode.normalise(" ۱۰۰۰۰۰۰۰۱۱ ")).isEqualTo("1000000011");
        assertThat(NationalCode.normalise("١٠٠٠٠٠٠٠١١")).isEqualTo("1000000011");
        assertThat(NationalCode.normalise(null)).isEmpty();
        assertThat(NationalCode.parse("۱۰۰۰۰۰۰۰۱۱")).map(NationalCode::value).contains("1000000011");
    }

    @Test
    @DisplayName("does not pad short codes with zeros")
    void doesNotPad() {
        assertThat(NationalCode.parse("12345674")).isEmpty();
    }

    @Test
    void constructorRejectsInvalidValueAndToStringReturnsValue() {
        assertThatThrownBy(() -> new NationalCode("1000000012")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new NationalCode("1000000011")).hasToString("1000000011");
    }
}
