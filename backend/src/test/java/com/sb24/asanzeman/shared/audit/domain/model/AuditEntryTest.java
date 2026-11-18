package com.sb24.asanzeman.shared.audit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AuditEntryTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENTITY = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static AuditEntry entry(String entityType, String action, String userName, String userRole, String reason,
            String ip, String userAgent, String correlationId) {
        return new AuditEntry(USER, userName, userRole, entityType, ENTITY, action, "old", "new", reason, ip,
                userAgent, correlationId);
    }

    @Test
    void keepsEveryFieldOfAValidEntry() {
        AuditEntry e = new AuditEntry(USER, "ali", "BRANCH", "REQUEST", ENTITY, "LOCK", "A", "B", "why", "10.0.0.1",
                "curl", "corr-1");

        assertThat(e.userId()).isEqualTo(USER);
        assertThat(e.userName()).isEqualTo("ali");
        assertThat(e.userRole()).isEqualTo("BRANCH");
        assertThat(e.entityType()).isEqualTo("REQUEST");
        assertThat(e.entityId()).isEqualTo(ENTITY);
        assertThat(e.action()).isEqualTo("LOCK");
        assertThat(e.oldValue()).isEqualTo("A");
        assertThat(e.newValue()).isEqualTo("B");
        assertThat(e.reason()).isEqualTo("why");
        assertThat(e.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(e.userAgent()).isEqualTo("curl");
        assertThat(e.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void optionalFieldsMayBeNull() {
        AuditEntry e = new AuditEntry(null, null, null, "REQUEST", null, "LOCK", null, null, null, null, null, null);

        assertThat(e.userName()).isNull();
        assertThat(e.reason()).isNull();
    }

    @Test
    void acceptsValuesExactlyAtTheColumnLimit() {
        AuditEntry e = entry("t".repeat(AuditEntry.MAX_ENTITY_TYPE), "a".repeat(AuditEntry.MAX_ACTION),
                "n".repeat(AuditEntry.MAX_USER_NAME), "r".repeat(AuditEntry.MAX_USER_ROLE),
                "x".repeat(AuditEntry.MAX_REASON), "i".repeat(AuditEntry.MAX_IP),
                "u".repeat(AuditEntry.MAX_USER_AGENT), "c".repeat(AuditEntry.MAX_CORRELATION_ID));

        assertThat(e.action()).hasSize(AuditEntry.MAX_ACTION);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void entityTypeIsRequired(String blank) {
        assertThatThrownBy(() -> entry(blank, "LOCK", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("entityType is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void actionIsRequired(String blank) {
        assertThatThrownBy(() -> entry("REQUEST", blank, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("action is required");
    }

    @Test
    void rejectsTooLongRequiredFields() {
        assertThatThrownBy(() -> entry("t".repeat(AuditEntry.MAX_ENTITY_TYPE + 1), "LOCK", null, null, null, null,
                null, null)).hasMessage("entityType is longer than 64 characters");
        assertThatThrownBy(() -> entry("REQUEST", "a".repeat(AuditEntry.MAX_ACTION + 1), null, null, null, null,
                null, null)).hasMessage("action is longer than 32 characters");
    }

    @Test
    void rejectsTooLongOptionalFields() {
        assertThatThrownBy(() -> entry("REQUEST", "LOCK", "n".repeat(AuditEntry.MAX_USER_NAME + 1), null, null, null,
                null, null)).hasMessage("userName is longer than 100 characters");
        assertThatThrownBy(() -> entry("REQUEST", "LOCK", null, "r".repeat(AuditEntry.MAX_USER_ROLE + 1), null, null,
                null, null)).hasMessage("userRole is longer than 16 characters");
        assertThatThrownBy(() -> entry("REQUEST", "LOCK", null, null, "x".repeat(AuditEntry.MAX_REASON + 1), null,
                null, null)).hasMessage("reason is longer than 500 characters");
        assertThatThrownBy(() -> entry("REQUEST", "LOCK", null, null, null, "i".repeat(AuditEntry.MAX_IP + 1), null,
                null)).hasMessage("ipAddress is longer than 45 characters");
        assertThatThrownBy(() -> entry("REQUEST", "LOCK", null, null, null, null,
                "u".repeat(AuditEntry.MAX_USER_AGENT + 1), null)).hasMessage("userAgent is longer than 500 characters");
        assertThatThrownBy(() -> entry("REQUEST", "LOCK", null, null, null, null, null,
                "c".repeat(AuditEntry.MAX_CORRELATION_ID + 1)))
                .hasMessage("correlationId is longer than 64 characters");
    }

    @Test
    void systemEntryHasNoUserAndTheSystemRole() {
        AuditEntry e = AuditEntry.system("REQUEST", ENTITY, "AUTO_RELEASE", "idle 60 min");

        assertThat(e.userId()).isNull();
        assertThat(e.userName()).isEqualTo("system");
        assertThat(e.userRole()).isEqualTo("SYSTEM");
        assertThat(e.entityType()).isEqualTo("REQUEST");
        assertThat(e.entityId()).isEqualTo(ENTITY);
        assertThat(e.action()).isEqualTo("AUTO_RELEASE");
        assertThat(e.reason()).isEqualTo("idle 60 min");
        assertThat(e.correlationId()).isNull();
    }
}
