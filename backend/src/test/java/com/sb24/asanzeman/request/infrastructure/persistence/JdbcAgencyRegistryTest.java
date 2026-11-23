package com.sb24.asanzeman.request.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import java.sql.Types;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcAgencyRegistryTest {

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcAgencyRegistry registry = new JdbcAgencyRegistry(jdbc);

    private UUID insert(String cif, String code, int active) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO azs_business.agency (id, cif, code, name, active) VALUES (:id, :cif, :code, 'Sky', :a)")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).param("cif", cif).param("code", code)
                .param("a", active).update();
        return id;
    }

    @Test
    void readsTheAgencyForRegistration() {
        UUID id = insert("9001", "0042", 1);

        var agency = registry.lock(id).orElseThrow();

        assertThat(agency.id()).isEqualTo(id);
        assertThat(agency.code()).isEqualTo("0042");
        assertThat(agency.name()).isEqualTo("Sky");
        assertThat(agency.active()).isTrue();
    }

    @Test
    void anInactiveAgencyIsReadAsInactive() {
        assertThat(registry.lock(insert("9002", "0043", 0)).orElseThrow().active()).isFalse();
    }

    @Test
    void theMobileNumberIsReadWhenTheAgencyHasOne() {
        UUID id = insert("9005", "0046", 1);
        assertThat(registry.find(id).orElseThrow().mobile()).isNull();

        jdbc.sql("UPDATE azs_business.agency SET mobile = '09129990000' WHERE id = :id")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).update();

        assertThat(registry.find(id).orElseThrow().mobile()).isEqualTo("09129990000");
        assertThat(registry.lock(id).orElseThrow().mobile()).isEqualTo("09129990000");
    }

    @Test
    void anUnknownAgencyIsEmpty() {
        assertThat(registry.lock(UUID.randomUUID())).isEmpty();
    }

    @Test
    void theRowIsReadWithForUpdateSoRegistrationsOfOneAgencyAreSerialised() {
        assertThat(JdbcAgencyRegistry.LOCK).endsWith("FOR UPDATE");
    }

    @Test
    void findReadsWithoutLockingAndShowsInactiveAgenciesToo() {
        UUID active = insert("9003", "0044", 1);
        UUID inactive = insert("9004", "0045", 0);

        assertThat(registry.find(active).orElseThrow().active()).isTrue();
        assertThat(registry.find(inactive).orElseThrow().active()).isFalse();
        assertThat(registry.find(UUID.randomUUID())).isEmpty();
    }
}
