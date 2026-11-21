package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CompanionAndCustomerTest {

    private static final String CODE = RequestTest.NATIONAL_CODE;
    private static final LocalDate BIRTH = LocalDate.of(2015, 1, 1);

    @Test
    void aCompanionKeepsTrimmedNameAndPassport() {
        var c = new Companion("  Sara Rezaei ", CODE, BIRTH, " P1 ");

        assertThat(c.fullName()).isEqualTo("Sara Rezaei");
        assertThat(c.passportNumber()).isEqualTo("P1");
    }

    @Test
    void theNameIsAtMostOneHundredCharacters() {
        assertThat(new Companion("x".repeat(100), CODE, BIRTH, "P1").fullName()).hasSize(100);
        assertThatThrownBy(() -> new Companion("x".repeat(101), CODE, BIRTH, "P1"))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> new Companion("  ", CODE, BIRTH, "P1"))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> new Companion(null, CODE, BIRTH, "P1"))
                .isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void theNationalCodeMustBeValid() {
        assertThatThrownBy(() -> new Companion("A", "0012345670", BIRTH, "P1"))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> new Companion("A", null, BIRTH, "P1"))
                .isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void aBirthDateAndAPassportAreRequired() {
        assertThatThrownBy(() -> new Companion("A", CODE, null, "P1")).isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> new Companion("A", CODE, BIRTH, " ")).isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> new Companion("A", CODE, BIRTH, null)).isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> new Companion("A", CODE, BIRTH, "x".repeat(31)))
                .isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void aCustomerNeedsATenDigitNationalCode() {
        assertThatThrownBy(() -> new CustomerSnapshot(null, "123", null, null, null, null, null, null, null, null,
                null, "ACTUAL", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CustomerSnapshot(null, null, null, null, null, null, null, null, null, null,
                null, "ACTUAL", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ageCountsWholeYears() {
        var c = RequestTest.customer(LocalDate.of(2000, 6, 15), "ACTUAL");

        assertThat(c.ageOn(LocalDate.of(2026, 6, 14))).isEqualTo(25);
        assertThat(c.ageOn(LocalDate.of(2026, 6, 15))).isEqualTo(26);
        assertThat(c.isActual()).isTrue();
        assertThat(RequestTest.customer(LocalDate.of(2000, 6, 15), "LEGAL").isActual()).isFalse();
    }
}
