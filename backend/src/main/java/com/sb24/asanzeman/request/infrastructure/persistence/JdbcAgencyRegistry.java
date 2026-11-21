package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AZS_BUSINESS.agency} for registration. The row is locked with {@code FOR UPDATE} (waiting, not
 * {@code NOWAIT}) so a second registration by the same agency waits for the first and then sees it (D47).
 */
@Component
public class JdbcAgencyRegistry implements AgencyRegistryPort {

    static final String LOCK = "SELECT id, code, name, active FROM azs_business.agency WHERE id = :id FOR UPDATE";

    private final JdbcClient jdbc;

    public JdbcAgencyRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AgencyInfo> lock(UUID agencyId) {
        return jdbc.sql(LOCK).param("id", RawUuid.toBytes(agencyId), Types.VARBINARY)
                .query((rs, row) -> new AgencyInfo(RawUuid.fromBytes(rs.getBytes("id")), rs.getString("code"),
                        rs.getString("name"), rs.getInt("active") == 1))
                .optional();
    }

    @Override
    public Optional<AgencyInfo> find(UUID agencyId) {
        return jdbc.sql(LOCK.replace(" FOR UPDATE", "")).param("id", RawUuid.toBytes(agencyId), Types.VARBINARY)
                .query((rs, row) -> new AgencyInfo(RawUuid.fromBytes(rs.getBytes("id")), rs.getString("code"),
                        rs.getString("name"), rs.getInt("active") == 1))
                .optional();
    }
}
