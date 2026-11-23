package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.AdminAgency;
import com.sb24.asanzeman.request.domain.model.AdminReason;
import com.sb24.asanzeman.request.domain.model.AgencyData;
import com.sb24.asanzeman.request.domain.model.AgencyDuplicateException;
import com.sb24.asanzeman.request.domain.model.ReasonData;
import com.sb24.asanzeman.request.domain.port.out.AdminReferencePort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The agency and rejection reason tables, as the admin edits them, through plain JDBC. */
@Component
public class JdbcAdminReference implements AdminReferencePort {

    private static final String AGENCIES = "SELECT id, cif, code, name, active, mobile FROM azs_business.agency";
    private static final String REASONS = "SELECT id, kind, text, active, sort_order FROM azs_business.rejection_reason";

    private static final RowMapper<AdminAgency> AGENCY = (rs, row) -> new AdminAgency(RawUuid.fromBytes(rs.getBytes("id")),
            new AgencyData(rs.getString("cif"), rs.getString("code"), rs.getString("name"),
                    rs.getInt("active") == 1, rs.getString("mobile")));
    private static final RowMapper<AdminReason> REASON = (rs, row) -> new AdminReason(RawUuid.fromBytes(rs.getBytes("id")),
            new ReasonData(rs.getString("kind"), rs.getString("text"), rs.getInt("active") == 1,
                    rs.getInt("sort_order")));

    private final JdbcClient jdbc;

    public JdbcAdminReference(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AdminAgency> agencies() {
        return jdbc.sql(AGENCIES + " ORDER BY code").query(AGENCY).list();
    }

    @Override
    public Optional<AdminAgency> findAgency(UUID id) {
        return jdbc.sql(AGENCIES + " WHERE id = :id").param("id", RawUuid.toBytes(id), Types.VARBINARY)
                .query(AGENCY).optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insertAgency(UUID id, AgencyData data, String by, Instant at) {
        try {
            jdbc.sql("INSERT INTO azs_business.agency (id, cif, code, name, active, mobile, created_by, updated_by) "
                    + "VALUES (:id, :cif, :code, :name, :active, :mobile, :by, :by)")
                    .param("id", RawUuid.toBytes(id), Types.VARBINARY).params(agencyParams(data))
                    .param("by", by, Types.VARCHAR).update();
        } catch (DuplicateKeyException e) {
            throw new AgencyDuplicateException();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateAgency(UUID id, AgencyData data, String by, Instant at) {
        try {
            jdbc.sql("UPDATE azs_business.agency SET cif = :cif, code = :code, name = :name, active = :active, "
                    + "mobile = :mobile, version = version + 1, updated_at = :at, updated_by = :by WHERE id = :id")
                    .param("id", RawUuid.toBytes(id), Types.VARBINARY).params(agencyParams(data))
                    .param("by", by, Types.VARCHAR).param("at", DbTime.toDb(at), Types.TIMESTAMP).update();
        } catch (DuplicateKeyException e) {
            throw new AgencyDuplicateException();
        }
    }

    private static java.util.Map<String, Object> agencyParams(AgencyData data) {
        var params = new java.util.HashMap<String, Object>();
        params.put("cif", data.cif());
        params.put("code", data.code());
        params.put("name", data.name());
        params.put("active", data.active() ? 1 : 0);
        params.put("mobile", data.mobile());
        return params;
    }

    @Override
    public List<AdminReason> reasons() {
        return jdbc.sql(REASONS + " ORDER BY kind, sort_order, text").query(REASON).list();
    }

    @Override
    public Optional<AdminReason> findReason(UUID id) {
        return jdbc.sql(REASONS + " WHERE id = :id").param("id", RawUuid.toBytes(id), Types.VARBINARY)
                .query(REASON).optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insertReason(UUID id, ReasonData data, String by, Instant at) {
        jdbc.sql("INSERT INTO azs_business.rejection_reason (id, kind, text, active, sort_order, created_by, updated_by) "
                + "VALUES (:id, :kind, :text, :active, :order, :by, :by)")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).param("kind", data.kind(), Types.VARCHAR)
                .param("text", data.text(), Types.VARCHAR).param("active", data.active() ? 1 : 0)
                .param("order", data.sortOrder()).param("by", by, Types.VARCHAR).update();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateReason(UUID id, ReasonData data, String by, Instant at) {
        jdbc.sql("UPDATE azs_business.rejection_reason SET kind = :kind, text = :text, active = :active, "
                + "sort_order = :order, updated_at = :at, updated_by = :by WHERE id = :id")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).param("kind", data.kind(), Types.VARCHAR)
                .param("text", data.text(), Types.VARCHAR).param("active", data.active() ? 1 : 0)
                .param("order", data.sortOrder()).param("by", by, Types.VARCHAR)
                .param("at", DbTime.toDb(at), Types.TIMESTAMP).update();
    }
}
