package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.JalaliDate;
import org.junit.jupiter.api.Test;

class TrackingCodeTest {

    private static final JalaliDate DAY = JalaliDate.of(1405, 8, 30);

    @Test
    void joinsBranchAgencyDayAndAFourDigitCounter() {
        assertThat(TrackingCode.of("0123", "0042", DAY, 7).value()).isEqualTo("01230042" + "050830" + "0007");
    }

    @Test
    void theLengthFollowsTheBranchCode() {
        assertThat(TrackingCode.of("123", "0042", DAY, 1).value()).hasSize(17);
        assertThat(TrackingCode.of("12345", "0042", DAY, 9999).value()).hasSize(19);
    }

    @Test
    void theBranchCodeKeepsItsLeadingZeroes() {
        assertThat(TrackingCode.of("0007", "0042", DAY, 1).value()).startsWith("0007");
    }

    @Test
    void toStringIsTheValue() {
        assertThat(TrackingCode.of("1", "0042", DAY, 1)).hasToString("100420508300001");
    }

    @Test
    void rejectsBadParts() {
        assertThatThrownBy(() -> TrackingCode.of(null, "0042", DAY, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingCode.of("12A", "0042", DAY, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingCode.of("123", null, DAY, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingCode.of("123", "42", DAY, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingCode.of("123", "0042", DAY, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackingCode.of("123", "0042", DAY, 10000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aStoredValueMustBe15To20Digits() {
        assertThat(new TrackingCode("1".repeat(15)).value()).hasSize(15);
        assertThat(new TrackingCode("1".repeat(20)).value()).hasSize(20);
        assertThatThrownBy(() -> new TrackingCode("1".repeat(14))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackingCode("1".repeat(21))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackingCode("1".repeat(14) + "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrackingCode(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anExhaustedCounterIsAConflict() {
        assertThat(new TrackingCodesExhaustedException().kind()).isEqualTo(DomainException.Kind.CONFLICT);
    }
}
