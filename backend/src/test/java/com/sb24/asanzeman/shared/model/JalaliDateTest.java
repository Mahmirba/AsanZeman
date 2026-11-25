package com.sb24.asanzeman.shared.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JalaliDateTest {

    @ParameterizedTest
    @CsvSource({
            "2026-11-16, 1405, 8, 25",
            "2025-03-21, 1404, 1, 1",
            "2024-03-20, 1403, 1, 1",
            "2024-03-19, 1402, 12, 29",
            "2025-03-20, 1403, 12, 30",
            "2000-01-01, 1378, 10, 11",
            "2026-09-23, 1405, 7, 1",
            "2026-10-22, 1405, 7, 30",
    })
    void convertsBothWays(LocalDate gregorian, int y, int m, int d) {
        JalaliDate jalali = JalaliDate.of(y, m, d);
        assertThat(JalaliDate.fromGregorian(gregorian)).isEqualTo(jalali);
        assertThat(jalali.toGregorian()).isEqualTo(gregorian);
    }

    @Test
    @DisplayName("round-trips every day from 1990 to 2060, including Gregorian century rules")
    void roundTripsWideRange() {
        for (LocalDate d = LocalDate.of(1990, 1, 1); d.isBefore(LocalDate.of(2060, 1, 1)); d = d.plusDays(1)) {
            assertThat(JalaliDate.fromGregorian(d).toGregorian()).isEqualTo(d);
        }
        // the 2100 non-leap century is after our horizon but exercises the century branch
        assertThat(JalaliDate.fromGregorian(LocalDate.of(2100, 3, 21)).toGregorian()).isEqualTo(LocalDate.of(2100, 3, 21));
    }

    @ParameterizedTest
    @CsvSource({"1403, true", "1404, false", "1405, false", "1399, true", "1408, true", "1412, true", "1416, true", "1420, true", "1421, false"})
    void leapYearsFollowThe33YearCycle(int year, boolean leap) {
        assertThat(JalaliDate.isLeap(year)).isEqualTo(leap);
        assertThat(JalaliDate.lengthOfMonth(year, 12)).isEqualTo(leap ? 30 : 29);
    }

    @Test
    void monthLengths() {
        assertThat(JalaliDate.lengthOfMonth(1405, 1)).isEqualTo(31);
        assertThat(JalaliDate.lengthOfMonth(1405, 6)).isEqualTo(31);
        assertThat(JalaliDate.lengthOfMonth(1405, 7)).isEqualTo(30);
        assertThat(JalaliDate.lengthOfMonth(1405, 11)).isEqualTo(30);
    }

    @Test
    void rejectsInvalidDates() {
        assertThatThrownBy(() -> JalaliDate.of(1405, 13, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JalaliDate.of(1405, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JalaliDate.of(1405, 7, 31)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JalaliDate.of(1404, 12, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JalaliDate.of(1405, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("formats for the tracking code (D12), Sepad and Sabte Ahval")
    void formats() {
        JalaliDate d = JalaliDate.of(1405, 8, 25);
        assertThat(d.toYyMmDd()).isEqualTo("050825");
        assertThat(d.toSlashed()).isEqualTo("1405/08/25");
        assertThat(d.toCompact()).isEqualTo("14050825");
        assertThat(d).hasToString("1405/08/25");
    }

    @Test
    void ordersChronologically() {
        assertThat(JalaliDate.of(1405, 8, 25)).isGreaterThan(JalaliDate.of(1405, 8, 24))
                .isGreaterThan(JalaliDate.of(1405, 7, 30))
                .isGreaterThan(JalaliDate.of(1404, 12, 29))
                .isEqualByComparingTo(JalaliDate.of(1405, 8, 25));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @ValueSource(strings = {"1405/08/30", " 1405/08/30 ", "۱۴۰۵/۰۸/۳۰", "١٤٠٥/٠٨/٣٠"})
    void parsesTheSrsFormatWithAnyDigits(String text) {
        assertThat(JalaliDate.parse(text)).isEqualTo(JalaliDate.of(1405, 8, 30));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @ValueSource(strings = {"", "1405-08-30", "1405/8/30", "1405/08", "1405/13/01",
            "1405/12/30", "abcd/ef/gh", "1405/08/30/1", "1405/08/3", "ٰ", "٪/01/01", "ۺ/01/01"})
    void rejectsAnythingThatIsNotARealJalaliDate(String text) {
        Assertions.assertThatThrownBy(() -> JalaliDate.parse(text))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingDateIsRejected() {
        Assertions.assertThatThrownBy(() -> JalaliDate.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInstantIsDatedInTehranNotInUtc() {
        // 22:00 UTC on 20 March is 01:30 on 21 March in Tehran (UTC+3:30): the first day of 1403
        assertThat(JalaliDate.ofInstant(Instant.parse("2024-03-20T22:00:00Z")))
                .isEqualTo(JalaliDate.of(1403, 1, 2));
        assertThat(JalaliDate.ofInstant(Instant.parse("2024-03-20T10:00:00Z")))
                .isEqualTo(JalaliDate.of(1403, 1, 1));
        assertThat(JalaliDate.TEHRAN.getId()).isEqualTo("Asia/Tehran");
    }
}
