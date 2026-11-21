package com.sb24.asanzeman.shared.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Instants are stored in {@code TIMESTAMP} columns as UTC wall-clock time, so a value reads back the same whatever
 * the server's time zone or daylight saving is.
 */
public final class DbTime {

    private DbTime() {
    }

    /** @return the UTC date-time to bind, or {@code null} for {@code null} */
    public static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** @return the instant, or {@code null} for {@code null} */
    public static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
