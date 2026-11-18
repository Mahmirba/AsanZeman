package com.sb24.asanzeman.shared.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.web.CorrelationIdFilter;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class JdbcAuditLogAdapterTest {

    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ENTITY = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Mock
    private JdbcClient jdbc;
    @Mock
    private JdbcClient.StatementSpec spec;

    private JdbcAuditLogAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JdbcAuditLogAdapter(jdbc);
    }

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> appended(AuditEntry entry) {
        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.params(anyMap())).thenReturn(spec);
        adapter.append(entry);
        verify(jdbc).sql(JdbcAuditLogAdapter.INSERT);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(spec).params(captor.capture());
        verify(spec).update();
        return captor.getValue();
    }

    @Test
    void insertsEveryFieldIntoTheAuditTable() {
        AuditEntry entry = new AuditEntry(USER, "ali", "BRANCH", "REQUEST", ENTITY, "LOCK", "A", "B", "why",
                "10.0.0.1", "curl", "corr-1");

        Map<String, Object> p = appended(entry);

        assertThat(p).containsEntry("userName", "ali").containsEntry("userRole", "BRANCH")
                .containsEntry("entityType", "REQUEST").containsEntry("action", "LOCK")
                .containsEntry("oldValue", "A").containsEntry("newValue", "B").containsEntry("reason", "why")
                .containsEntry("ipAddress", "10.0.0.1").containsEntry("userAgent", "curl")
                .containsEntry("correlationId", "corr-1");
        assertThat((byte[]) p.get("userId")).isEqualTo(JdbcAuditLogAdapter.raw(USER));
        assertThat((byte[]) p.get("entityId")).isEqualTo(JdbcAuditLogAdapter.raw(ENTITY));
        assertThat(JdbcAuditLogAdapter.INSERT).contains("azs_audit.audit_log").doesNotContain("UPDATE", "DELETE");
    }

    @Test
    void usesTheCorrelationIdOfTheRunningRequestWhenTheEntryHasNone() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "from-mdc");

        Map<String, Object> p = appended(AuditEntry.system("REQUEST", ENTITY, "AUTO_RELEASE", null));

        assertThat(p).containsEntry("correlationId", "from-mdc");
    }

    @Test
    void leavesTheCorrelationIdNullWhenNeitherSourceHasOne() {
        Map<String, Object> p = appended(AuditEntry.system("REQUEST", ENTITY, "AUTO_RELEASE", null));

        assertThat(p).containsEntry("correlationId", null);
        assertThat(p.get("userId")).isNull();
    }

    @Test
    void rawIsTheSixteenBigEndianBytesOfTheUuid() {
        byte[] bytes = JdbcAuditLogAdapter.raw(USER);

        assertThat(bytes).hasSize(16);
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        assertThat(new UUID(buf.getLong(), buf.getLong())).isEqualTo(USER);
    }

    @Test
    void rawOfNullIsNull() {
        assertThat(JdbcAuditLogAdapter.raw(null)).isNull();
    }
}
