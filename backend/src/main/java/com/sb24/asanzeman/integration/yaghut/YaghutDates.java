package com.sb24.asanzeman.integration.yaghut;

import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yaghut writes times as {@code 2026-11-16T13:51:10IRST}: an ISO local date-time followed by a zone
 * <em>abbreviation</em>, which Java does not parse reliably. Iran no longer uses daylight saving, so {@code IRST}
 * is a fixed +03:30 ({@code IRDT}, +04:30, is kept for older data). Plain ISO offsets are accepted too.
 */
final class YaghutDates {

    private static final Pattern WITH_ABBREVIATION = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})([A-Za-z]{2,5})$");
        private YaghutDates() {
    }

    /** @return the instant, or {@code null} for a missing value */
    static Instant parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            Matcher m = WITH_ABBREVIATION.matcher(value);
            if (m.matches()) {
                return LocalDateTime.parse(m.group(1)).atZone(zoneOf(m.group(2))).toInstant();
            }
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("unreadable Yaghut date: " + text, e);
        }
    }

    /**
     * A date without a time: {@code 2000-01-31}, {@code 20000131}, or either followed by a time part
     * ({@code 2000-01-31T00:00:00IRST}), which is ignored.
     *
     * @return the date, or {@code null} for a missing value
     */
    static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        try {
            if (value.matches("^\\d{8}$")) {
                return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
            }
            if (value.matches("^\\d{4}-\\d{2}-\\d{2}([T ].*)?$")) {
                return LocalDate.parse(value.substring(0, 10));
            }
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("unreadable Yaghut date: " + text, e);
        }
        throw new IllegalArgumentException("unreadable Yaghut date: " + text);
    }

    private static ZoneId zoneOf(String abbreviation) {
        return switch (abbreviation.toUpperCase()) {
            case "IRST" -> ZoneOffset.of("+03:30");
            case "IRDT" -> ZoneOffset.of("+04:30");
            case "UTC", "GMT" -> ZoneOffset.UTC;
            default -> JalaliDate.TEHRAN;
        };
    }
}
