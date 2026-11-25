package com.sb24.asanzeman.auth.infrastructure.persistence;

import com.sb24.asanzeman.auth.domain.model.Agency;
import com.sb24.asanzeman.auth.domain.port.out.AgencyDirectoryPort;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code AZS_BUSINESS.agency} read through plain JDBC; the login only looks agencies up by {@code cif}. */
@Component
public class JdbcAgencyDirectory implements AgencyDirectoryPort {

    static final String SELECT = "SELECT id, cif, name, active FROM azs_business.agency WHERE cif = :cif";

    private final JdbcClient jdbc;

    public JdbcAgencyDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Agency> findById(java.util.UUID id) {
        return jdbc.sql("SELECT id, cif, name, active FROM azs_business.agency WHERE id = :id")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY)
                .query((rs, row) -> new Agency(RawUuid.fromBytes(rs.getBytes("id")), rs.getString("cif"),
                        rs.getString("name"), rs.getInt("active") == 1))
                .optional();
    }

    @Override
    public Optional<Agency> findByCif(String cif) {
        return jdbc.sql(SELECT).param("cif", cif, Types.VARCHAR)
                .query((rs, row) -> new Agency(RawUuid.fromBytes(rs.getBytes("id")), rs.getString("cif"),
                        rs.getString("name"), rs.getInt("active") == 1))
                .optional();
    }
}
