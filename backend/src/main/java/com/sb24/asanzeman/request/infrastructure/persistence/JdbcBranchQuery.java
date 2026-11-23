package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.BranchSearchCriteria;
import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.port.out.BranchQueryPort;
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
 * The branch's inbox and search (SRS review use case, 4.4) through plain JDBC. The branch rule is part of the SQL: a
 * request is visible when its own branch, or the branch that handles it now, is the user's. Every value is a bind
 * parameter. A request that someone holds is still listed, with the holder's name, so every branch user sees it
 * read-only (4.1).
 */
@Component
public class JdbcBranchQuery implements BranchQueryPort {

    static final String COLUMNS = JdbcRequestQuery.COLUMNS.stripTrailing() + """
            , r.locked_by_name, r.locked_at, b.parent_branch AS handling_branch, rr.text AS rejection_reason,
              r.rejection_comment, r.guarantee_number
            """;
    static final String FROM = JdbcRequestQuery.FROM + """
              LEFT JOIN azs_business.rejection_reason rr ON rr.id = r.rejection_reason_id
            """;

    private final JdbcClient jdbc;

    public JdbcBranchQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<BranchRequestRow> search(BranchSearchCriteria c, int page, int size) {
        Map<String, Object> params = new TreeMap<>();
        String where = where(c, params);
        Long total = jdbc.sql("SELECT COUNT(*)" + FROM + where).params(params).query(Long.class).single();
        List<BranchRequestRow> items = total == 0 ? List.of()
                : jdbc.sql(COLUMNS + FROM + where + " ORDER BY r.registered_at DESC, r.id"
                        + " OFFSET " + ((long) page * size) + " ROWS FETCH NEXT " + size + " ROWS ONLY")
                        .params(params).query((rs, row) -> row(rs)).list();
        return new PageResult<>(items, total, page, size);
    }

    @Override
    public Optional<BranchRequestRow> find(UUID requestId) {
        return jdbc.sql(COLUMNS + FROM + " WHERE r.id = :id AND r.status <> 'REGISTERED_AT_AGENCY'")
                .param("id", RawUuid.toBytes(requestId), Types.VARBINARY).query((rs, row) -> row(rs)).optional();
    }

    @Override
    public List<Companion> companions(UUID requestId) {
        return jdbc.sql(JdbcRequestQuery.SELECT_COMPANIONS).param("id", RawUuid.toBytes(requestId), Types.VARBINARY)
                .query((rs, row) -> new Companion(rs.getString("full_name"), rs.getString("national_code"),
                        rs.getObject("birth_date", LocalDate.class), rs.getString("passport_number"))).list();
    }

    private static String where(BranchSearchCriteria c, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
        conditions.add("r.status IN (:statuses)");
        params.put("statuses", c.statuses().stream().map(RequestStatus::name).sorted().toList());
        if (c.branchCode() != null) {
            conditions.add("(r.branch_code = :branch OR b.parent_branch = :branch)");
            params.put("branch", c.branchCode());
        }
        if (JdbcRequestQuery.notBlank(c.customerCode())) {
            conditions.add("r.customer_code = :code");
            params.put("code", c.customerCode().trim());
        }
        if (JdbcRequestQuery.notBlank(c.customerCif())) {
            conditions.add("r.customer_cif = :cif");
            params.put("cif", c.customerCif().trim());
        }
        if (JdbcRequestQuery.notBlank(c.trackingCode())) {
            conditions.add("r.tracking_code = :tracking");
            params.put("tracking", c.trackingCode().trim());
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

    private static BranchRequestRow row(ResultSet rs) throws SQLException {
        LocalDateTime lockedAt = rs.getObject("locked_at", LocalDateTime.class);
        return new BranchRequestRow(JdbcRequestQuery.summary(rs), rs.getString("locked_by_name"),
                lockedAt == null ? null : DbTime.fromDb(lockedAt), rs.getString("handling_branch"),
                rs.getString("rejection_reason"), rs.getString("rejection_comment"),
                rs.getString("guarantee_number"));
    }
}
