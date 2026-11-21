package com.sb24.asanzeman.shared.audit.infrastructure.persistence;

import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.out.AuditLogPort;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.shared.web.CorrelationIdFilter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Plain JDBC insert into {@code AZS_AUDIT.audit_log}: the table is append-only and the application user has INSERT
 * only (db/dba), so there is no JPA entity to update or delete.
 */
@Component
public class JdbcAuditLogAdapter implements AuditLogPort {

    static final String INSERT = """
            INSERT INTO azs_audit.audit_log
                (user_id, user_name, user_role, entity_type, entity_id, action,
                 old_value, new_value, reason, ip_address, user_agent, correlation_id)
            VALUES
                (:userId, :userName, :userRole, :entityType, :entityId, :action,
                 :oldValue, :newValue, :reason, :ipAddress, :userAgent, :correlationId)
            """;

    private final JdbcClient jdbc;

    public JdbcAuditLogAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(AuditEntry entry) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", raw(entry.userId()));
        params.put("userName", entry.userName());
        params.put("userRole", entry.userRole());
        params.put("entityType", entry.entityType());
        params.put("entityId", raw(entry.entityId()));
        params.put("action", entry.action());
        params.put("oldValue", entry.oldValue());
        params.put("newValue", entry.newValue());
        params.put("reason", entry.reason());
        params.put("ipAddress", entry.ipAddress());
        params.put("userAgent", entry.userAgent());
        params.put("correlationId", entry.correlationId() != null
                ? entry.correlationId()
                : MDC.get(CorrelationIdFilter.MDC_KEY));
        jdbc.sql(INSERT).params(params).update();
    }

    /** UUID as the 16 bytes of an Oracle {@code RAW(16)}, or {@code null}. */
    static byte[] raw(UUID id) {
        return RawUuid.toBytes(id);
    }
}
