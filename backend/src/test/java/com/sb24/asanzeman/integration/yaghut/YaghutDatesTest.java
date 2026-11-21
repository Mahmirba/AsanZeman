package com.sb24.asanzeman.integration.yaghut;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class YaghutDatesTest {

    @Test
    void parsesTheIrstTimeFromTheRealLoginAnswer() {
        // 13:51:10 at +03:30 is 10:21:10 UTC
        assertThat(YaghutDates.parse("2026-11-16T13:51:10IRST")).isEqualTo(Instant.parse("2026-11-16T10:21:10Z"));
    }

    @Test
    void irdtIsAnHourAhead() {
        assertThat(YaghutDates.parse("2026-07-16T13:51:10IRDT")).isEqualTo(Instant.parse("2026-07-16T09:21:10Z"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-16T10:21:10UTC", "2026-11-16T10:21:10GMT", "2026-11-16T10:21:10Z",
            "2026-11-16T13:51:10+03:30", "  2026-11-16T10:21:10utc  "})
    void parsesUtcNamesAndIsoOffsets(String text) {
        assertThat(YaghutDates.parse(text)).isEqualTo(Instant.parse("2026-11-16T10:21:10Z"));
    }

    @Test
    void anUnknownAbbreviationIsReadAsTehranTime() {
        assertThat(YaghutDates.parse("2026-11-16T13:51:10XYZ")).isEqualTo(Instant.parse("2026-11-16T10:21:10Z"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aMissingValueIsNull(String text) {
        assertThat(YaghutDates.parse(text)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"yesterday", "2026-13-45T25:61:61IRST", "2026-11-16"})
    void anUnreadableValueIsRefused(String text) {
        assertThatThrownBy(() -> YaghutDates.parse(text)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unreadable Yaghut date");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1990-05-17", "19900517", " 1990-05-17 ", "1990-05-17T00:00:00IRST", "1990-05-17 00:00:00"})
    void parsesADateWithOrWithoutATimePart(String text) {
        assertThat(YaghutDates.parseDate(text)).isEqualTo(java.time.LocalDate.of(1990, 5, 17));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aMissingDateIsNull(String text) {
        assertThat(YaghutDates.parseDate(text)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"soon", "1990/05/17", "1990-13-45", "19901345"})
    void anUnreadableDateIsRejected(String text) {
        assertThatThrownBy(() -> YaghutDates.parseDate(text)).isInstanceOf(IllegalArgumentException.class);
    }
}
