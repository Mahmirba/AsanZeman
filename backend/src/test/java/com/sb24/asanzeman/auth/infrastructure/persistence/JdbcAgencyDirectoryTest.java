package com.sb24.asanzeman.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import java.sql.Types;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcAgencyDirectoryTest {

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcAgencyDirectory directory = new JdbcAgencyDirectory(jdbc);

    private UUID insert(String cif, int active) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO azs_business.agency (id, cif, code, name, active) VALUES (:id, :cif, :cif, 'Sky', :a)")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).param("cif", cif).param("a", active).update();
        return id;
    }

    @Test
    void findsAnAgencyByCif() {
        UUID id = insert("9001", 1);

        var agency = directory.findByCif("9001").orElseThrow();

        assertThat(agency.id()).isEqualTo(id);
        assertThat(agency.name()).isEqualTo("Sky");
        assertThat(agency.active()).isTrue();
    }

    @Test
    void anInactiveAgencyIsReadAsInactive() {
        insert("9002", 0);

        assertThat(directory.findByCif("9002").orElseThrow().active()).isFalse();
    }

    @Test
    void findsAnAgencyByItsIdInactiveOnesToo() {
        UUID active = insert("9003", 1);
        UUID inactive = insert("9004", 0);

        assertThat(directory.findById(active).orElseThrow().active()).isTrue();
        assertThat(directory.findById(inactive).orElseThrow().active()).isFalse();
        assertThat(directory.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void anUnknownCifIsEmpty() {
        assertThat(directory.findByCif("0000")).isEmpty();
    }
}
