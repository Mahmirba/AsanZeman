package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** {@code AZS_BUSINESS.request} and {@code request_companion} through plain JDBC (D49: no JPA entity needed yet). */
@Component
public class JdbcRequestRepository implements RequestRepositoryPort {

    static final String INSERT = """
            INSERT INTO azs_business.request (id, tracking_code, request_type, status, parent_request_id, agency_id,
                branch_code, amount, due_date, customer_cif, customer_code, customer_first_name, customer_last_name,
                customer_full_name, customer_father_name, customer_birth_date, customer_mobile, customer_ssn,
                customer_cert_serial, customer_cert_series, customer_personality, customer_gender, registered_by,
                registered_at, created_by)
            VALUES (:id, :tracking, :type, :status, NULL, :agency, :branch, :amount, :due, :cif, :code, :first, :last,
                :full, :father, :birth, :mobile, :ssn, :serial, :series, :personality, :gender, :by, :at, :by)
            """;
    static final String INSERT_COMPANION = """
            INSERT INTO azs_business.request_companion (id, request_id, sort_order, full_name, national_code,
                birth_date, passport_number)
            VALUES (:id, :request, :order, :name, :code, :birth, :passport)
            """;
    static final String SELECT = """
            SELECT id, tracking_code, request_type, status, agency_id, branch_code, amount, due_date, customer_cif,
                   customer_code, customer_first_name, customer_last_name, customer_full_name, customer_father_name,
                   customer_birth_date, customer_mobile, customer_ssn, customer_cert_serial, customer_cert_series,
                   customer_personality, customer_gender, registered_by, registered_at
              FROM azs_business.request WHERE id = :id
            """;
    static final String SELECT_COMPANIONS = """
            SELECT full_name, national_code, birth_date, passport_number
              FROM azs_business.request_companion WHERE request_id = :id ORDER BY sort_order
            """;

    private final JdbcClient jdbc;

    public JdbcRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Request r) {
        CustomerSnapshot c = r.customer();
        jdbc.sql(INSERT)
                .param("id", RawUuid.toBytes(r.id()), Types.VARBINARY)
                .param("tracking", r.trackingCode().value(), Types.VARCHAR)
                .param("type", r.type().name(), Types.VARCHAR)
                .param("status", r.status().name(), Types.VARCHAR)
                .param("agency", RawUuid.toBytes(r.agencyId()), Types.VARBINARY)
                .param("branch", r.branchCode(), Types.VARCHAR)
                .param("amount", r.amount().value(), Types.NUMERIC)
                .param("due", r.dueDate(), Types.DATE)
                .param("cif", c.cif(), Types.VARCHAR).param("code", c.code(), Types.VARCHAR)
                .param("first", c.firstName(), Types.VARCHAR).param("last", c.lastName(), Types.VARCHAR)
                .param("full", c.fullName(), Types.VARCHAR).param("father", c.fatherName(), Types.VARCHAR)
                .param("birth", c.birthDate(), Types.DATE).param("mobile", c.mobile(), Types.VARCHAR)
                .param("ssn", c.ssn(), Types.VARCHAR).param("serial", c.certificateSerial(), Types.VARCHAR)
                .param("series", c.certificateSeries(), Types.VARCHAR)
                .param("personality", c.personality(), Types.VARCHAR).param("gender", c.gender(), Types.VARCHAR)
                .param("by", r.registeredBy(), Types.VARCHAR)
                .param("at", DbTime.toDb(r.registeredAt()), Types.TIMESTAMP)
                .update();
        int order = 1;
        for (Companion p : r.companions()) {
            jdbc.sql(INSERT_COMPANION)
                    .param("id", RawUuid.toBytes(UUID.randomUUID()), Types.VARBINARY)
                    .param("request", RawUuid.toBytes(r.id()), Types.VARBINARY)
                    .param("order", order++, Types.NUMERIC)
                    .param("name", p.fullName(), Types.VARCHAR).param("code", p.nationalCode(), Types.VARCHAR)
                    .param("birth", p.birthDate(), Types.DATE).param("passport", p.passportNumber(), Types.VARCHAR)
                    .update();
        }
    }

    @Override
    public Optional<Request> findById(UUID id) {
        List<Companion> companions = jdbc.sql(SELECT_COMPANIONS).param("id", RawUuid.toBytes(id), Types.VARBINARY)
                .query((rs, row) -> new Companion(rs.getString("full_name"), rs.getString("national_code"),
                        rs.getObject("birth_date", LocalDate.class), rs.getString("passport_number")))
                .list();
        return jdbc.sql(SELECT).param("id", RawUuid.toBytes(id), Types.VARBINARY)
                .query((rs, row) -> new Request(RawUuid.fromBytes(rs.getBytes("id")),
                        new TrackingCode(rs.getString("tracking_code")),
                        RequestType.valueOf(rs.getString("request_type")),
                        RequestStatus.valueOf(rs.getString("status")),
                        RawUuid.fromBytes(rs.getBytes("agency_id")), rs.getString("branch_code"),
                        Rials.of(rs.getLong("amount")), rs.getObject("due_date", LocalDate.class),
                        new CustomerSnapshot(rs.getString("customer_cif"), rs.getString("customer_code"),
                                rs.getString("customer_first_name"), rs.getString("customer_last_name"),
                                rs.getString("customer_full_name"), rs.getString("customer_father_name"),
                                rs.getObject("customer_birth_date", LocalDate.class),
                                rs.getString("customer_mobile"), rs.getString("customer_ssn"),
                                rs.getString("customer_cert_serial"), rs.getString("customer_cert_series"),
                                rs.getString("customer_personality"), rs.getString("customer_gender")),
                        companions, rs.getString("registered_by"),
                        DbTime.fromDb(rs.getObject("registered_at", java.time.LocalDateTime.class))))
                .optional();
    }

    @Override
    public java.util.List<UUID> incompleteSince(java.time.Instant before) {
        return jdbc.sql("SELECT id FROM azs_business.request WHERE request_type = 'ISSUANCE' "
                + "AND status = 'REGISTERED_AT_AGENCY' AND registered_at <= :before ORDER BY registered_at")
                .param("before", DbTime.toDb(before), Types.TIMESTAMP)
                .query((rs, row) -> RawUuid.fromBytes(rs.getBytes("id"))).list();
    }

    @Override
    public boolean existsSince(UUID agencyId, String customerCode, java.time.Instant since) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM azs_business.request
                 WHERE agency_id = :agency AND customer_code = :code AND registered_at >= :since""")
                .param("agency", RawUuid.toBytes(agencyId), Types.VARBINARY)
                .param("code", customerCode, Types.VARCHAR)
                .param("since", DbTime.toDb(since), Types.TIMESTAMP)
                .query(Integer.class).single() > 0;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Request> findByIdForUpdate(UUID id) {
        try {
            jdbc.sql("SELECT id FROM azs_business.request WHERE id = :id FOR UPDATE NOWAIT")
                    .param("id", RawUuid.toBytes(id), Types.VARBINARY).query((rs, row) -> 1).optional();
        } catch (org.springframework.dao.PessimisticLockingFailureException e) {
            // ORA-00054: another transaction holds the row (4.1)
            throw new com.sb24.asanzeman.request.domain.model.RequestBusyException();
        }
        return findById(id);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateStatus(UUID id, RequestStatus status, java.time.Instant at) {
        jdbc.sql("UPDATE azs_business.request SET status = :status, version = version + 1, updated_at = :at "
                + "WHERE id = :id")
                .param("status", status.name(), Types.VARCHAR).param("at", DbTime.toDb(at), Types.TIMESTAMP)
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).update();
    }
}
