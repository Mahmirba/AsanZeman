package com.sb24.asanzeman.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class RawUuidAndDbTimeTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void aUuidRoundTripsThroughSixteenBytes() {
        byte[] bytes = RawUuid.toBytes(ID);

        assertThat(bytes).hasSize(16);
        assertThat(RawUuid.fromBytes(bytes)).isEqualTo(ID);
    }

    @Test
    void nullStaysNull() {
        assertThat(RawUuid.toBytes(null)).isNull();
        assertThat(RawUuid.fromBytes(null)).isNull();
        assertThat(DbTime.toDb(null)).isNull();
        assertThat(DbTime.fromDb(null)).isNull();
    }

    @Test
    void anInstantIsStoredAsUtcWallClockTimeWhateverTheServerZone() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"));
            Instant instant = Instant.parse("2026-11-17T10:21:10Z");

            LocalDateTime stored = DbTime.toDb(instant);

            assertThat(stored).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 21, 10));
            assertThat(DbTime.fromDb(stored)).isEqualTo(instant);
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
