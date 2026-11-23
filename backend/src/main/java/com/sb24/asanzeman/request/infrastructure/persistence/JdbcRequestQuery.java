package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RequestDetails;
import com.sb24.asanzeman.request.domain.model.RequestSearchCriteria;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.port.out.RequestQueryPort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The agency's tracking list and details (SRS UC-AZS-13) through plain JDBC. The filters are built as a WHERE clause
 * from the criteria that are set; every value is a bind parameter, never text in the SQL. Only the agency filter is
 * always there, so an agency can never read another agency's requests. Newest first, with a stable tie-break.
 */
@Component
public class JdbcRequestQuery implements RequestQueryPort {

    static final String FROM = """
              FROM azs_business.request r
              JOIN azs_business.agency a ON a.id = r.agency_id
              JOIN azs_business.branch b ON b.branch_code = r.branch_code
            """;
    static final String COLUMNS = """
            SELECT r.id, r.tracking_code, r.request_type, r.status, r.customer_code, r.customer_cif,
                   r.customer_full_name, r.amount, r.branch_code, b.name AS branch_name, r.due_date,
                   r.registered_at, a.name AS agency_name, r.customer_mobile
            """;
    static final String SELECT_COMPANIONS = """
            SELECT full_name, national_code, birth_date, passport_number
              FROM azs_business.request_companion WHERE request_id = :id ORDER BY sort_order
            """;

    private final JdbcClient jdbc;

    public JdbcRequestQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<RequestSummary> search(RequestSearchCriteria c, int page, int size) {
        Map<String, Object> params = new TreeMap<>();
        String where = where(c, params);
        Long total = query("SELECT COUNT(*)" + FROM + where, params).query(Long.class).single();
        List<RequestSummary> items = total == 0 ? List.of()
                : query(COLUMNS + FROM + where + " ORDER BY r.registered_at DESC, r.id"
                        + " OFFSET " + ((long) page * size) + " ROWS FETCH NEXT " + size + " ROWS ONLY", params)
                        .query((rs, row) -> summary(rs)).list();
        return new PageResult<>(items, total, page, size);
    }

    @Override
    public Optional<RequestDetails> details(UUID agencyId, UUID requestId) {
        return details(" WHERE r.id = :id AND r.agency_id = :owner", requestId, RawUuid.toBytes(agencyId));
    }

    @Override
    public List<RequestSummary> forCustomer(String nationalCode) {
        return jdbc.sql(COLUMNS + FROM + " WHERE r.customer_code = :code ORDER BY r.registered_at DESC, r.id")
                .param("code", nationalCode).query((rs, row) -> summary(rs)).list();
    }

    @Override
    public Optional<RequestDetails> detailsForCustomer(String nationalCode, UUID requestId) {
        return details(" WHERE r.id = :id AND r.customer_code = :owner", requestId, nationalCode);
    }

    /** One request and its companions; {@code where} also restricts it to its owner, the {@code :owner} value. */
    private Optional<RequestDetails> details(String where, UUID requestId, Object owner) {
        Optional<RequestSummary> summary = jdbc.sql(COLUMNS + FROM + where)
                .param("id", RawUuid.toBytes(requestId), Types.VARBINARY)
                .param("owner", owner)
                .query((rs, row) -> summary(rs)).optional();
        return summary.map(s -> new RequestDetails(s, jdbc.sql(SELECT_COMPANIONS)
                .param("id", RawUuid.toBytes(requestId), Types.VARBINARY)
                .query((rs, row) -> new Companion(rs.getString("full_name"), rs.getString("national_code"),
                        rs.getObject("birth_date", LocalDate.class), rs.getString("passport_number")))
                .list()));
    }

    private static String where(RequestSearchCriteria c, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
        conditions.add("r.agency_id = :agency");
        params.put("agency", RawUuid.toBytes(c.agencyId()));
        if (notBlank(c.customerCode())) {
            conditions.add("r.customer_code = :code");
            params.put("code", c.customerCode().trim());
        }
        if (notBlank(c.customerCif())) {
            conditions.add("r.customer_cif = :cif");
            params.put("cif", c.customerCif().trim());
        }
        if (!c.types().isEmpty()) {
            conditions.add("r.request_type IN (:types)");
            params.put("types", c.types().stream().map(RequestType::name).sorted().toList());
        }
        if (notBlank(c.trackingCode())) {
            conditions.add("r.tracking_code = :tracking");
            params.put("tracking", c.trackingCode().trim());
        }
        if (notBlank(c.name())) {
            conditions.add("r.customer_full_name LIKE :name ESCAPE '\\'");
            params.put("name", "%" + escapeLike(c.name().trim()) + "%");
        }
        if (c.registeredFrom() != null) {
            conditions.add("r.registered_at >= :from");
            params.put("from", DbTime.toDb(c.registeredFrom()));
        }
        if (c.registeredTo() != null) {
            conditions.add("r.registered_at < :to");
            params.put("to", DbTime.toDb(c.registeredTo()));
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private JdbcClient.StatementSpec query(String sql, Map<String, Object> params) {
        return jdbc.sql(sql).params(params);
    }

    /** Makes {@code %}, {@code _} and the escape character itself plain text in a LIKE pattern. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    static RequestSummary summary(ResultSet rs) throws SQLException {
        return new RequestSummary(RawUuid.fromBytes(rs.getBytes("id")), rs.getString("tracking_code"),
                RequestType.valueOf(rs.getString("request_type")), RequestStatus.valueOf(rs.getString("status")),
                rs.getString("customer_code"), rs.getString("customer_cif"), rs.getString("customer_full_name"),
                rs.getLong("amount"), rs.getString("branch_code"), rs.getString("branch_name"),
                rs.getObject("due_date", LocalDate.class),
                DbTime.fromDb(rs.getObject("registered_at", LocalDateTime.class)), rs.getString("agency_name"),
                rs.getString("customer_mobile"));
    }
}
