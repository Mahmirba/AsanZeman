package com.sb24.asanzeman.shared.audit.domain.model;

import java.util.UUID;

/**
 * One row of the append-only audit log (6-5, {@code AZS_AUDIT.audit_log}). Who did what to which entity, and why.
 * Nothing is masked (D22). Field limits mirror the column sizes of migration V002.
 *
 * @param userId        acting user, or {@code null} for system actions (scheduler jobs)
 * @param userName      acting user's display name ({@code accountName} for bank users, D33)
 * @param userRole      BRANCH, ADMIN, AGENCY, CUSTOMER or SYSTEM
 * @param entityType    kind of entity touched, e.g. {@code REQUEST}
 * @param entityId      id of that entity
 * @param action        what happened, e.g. {@code LOCK}, {@code FORCE_RELEASE}
 * @param oldValue      state before, as text, optional
 * @param newValue      state after, as text, optional
 * @param reason        free-text reason (mandatory for force-release), optional
 * @param ipAddress     caller address, optional
 * @param userAgent     caller user agent, optional
 * @param correlationId request correlation id; when {@code null} the adapter uses the one in the logging context
 */
public record AuditEntry(
        UUID userId,
        String userName,
        String userRole,
        String entityType,
        UUID entityId,
        String action,
        String oldValue,
        String newValue,
        String reason,
        String ipAddress,
        String userAgent,
        String correlationId) {

    static final int MAX_USER_NAME = 100;
    static final int MAX_USER_ROLE = 16;
    static final int MAX_ENTITY_TYPE = 64;
    static final int MAX_ACTION = 32;
    static final int MAX_REASON = 500;
    static final int MAX_IP = 45;
    static final int MAX_USER_AGENT = 500;
    static final int MAX_CORRELATION_ID = 64;

    public AuditEntry {
        entityType = required("entityType", entityType, MAX_ENTITY_TYPE);
        action = required("action", action, MAX_ACTION);
        optional("userName", userName, MAX_USER_NAME);
        optional("userRole", userRole, MAX_USER_ROLE);
        optional("reason", reason, MAX_REASON);
        optional("ipAddress", ipAddress, MAX_IP);
        optional("userAgent", userAgent, MAX_USER_AGENT);
        optional("correlationId", correlationId, MAX_CORRELATION_ID);
    }

    /** An audit row for an action done by the system itself (scheduler, auto-release). */
    public static AuditEntry system(String entityType, UUID entityId, String action, String reason) {
        return new AuditEntry(null, "system", "SYSTEM", entityType, entityId, action, null, null, reason, null, null,
                null);
    }

    private static String required(String field, String value, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        optional(field, value, max);
        return value;
    }

    private static void optional(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " is longer than " + max + " characters");
        }
    }
}
