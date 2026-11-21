package com.sb24.asanzeman.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcYaghutSessionStoreTest {

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcYaghutSessionStore store = new JdbcYaghutSessionStore(jdbc);

    private String sessionOf(UUID family) {
        return jdbc.sql("SELECT jsession_id FROM azs_auth.yaghut_session WHERE family_id = :f")
                .param("f", RawUuid.toBytes(family), Types.VARBINARY).query(String.class).single();
    }

    @Test
    void savesTheSessionUnderItsFamily() {
        UUID family = UUID.randomUUID();

        store.save(family, "S1", Instant.parse("2026-11-17T11:00:00Z"));

        assertThat(sessionOf(family)).isEqualTo("S1");
    }

    @Test
    void anUnknownEndTimeIsStoredAsNull() {
        UUID family = UUID.randomUUID();

        store.save(family, "S2", null);

        assertThat(sessionOf(family)).isEqualTo("S2");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM azs_auth.yaghut_session WHERE expires_at IS NULL")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void findsASavedSessionWithItsEndTime() {
        UUID family = UUID.randomUUID();
        Instant end = Instant.parse("2026-11-17T11:00:00Z");
        store.save(family, "S3", end);

        var found = store.find(family).orElseThrow();

        assertThat(found.jsessionId()).isEqualTo("S3");
        assertThat(found.expiresAt()).isEqualTo(end);
    }

    @Test
    void aSessionWithoutAnEndTimeIsFoundWithANullEnd() {
        UUID family = UUID.randomUUID();
        store.save(family, "S4", null);

        assertThat(store.find(family).orElseThrow().expiresAt()).isNull();
    }

    @Test
    void anUnknownFamilyIsEmpty() {
        assertThat(store.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deleteRemovesOnlyThatSession() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.save(a, "A", null);
        store.save(b, "B", null);

        store.delete(a);

        assertThat(store.find(a)).isEmpty();
        assertThat(store.find(b)).isPresent();
    }
}
