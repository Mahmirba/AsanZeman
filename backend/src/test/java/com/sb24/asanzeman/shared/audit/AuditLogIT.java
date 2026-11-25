package com.sb24.asanzeman.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.support.OracleTestContainer;
import com.sb24.asanzeman.support.TestKeys;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** Writes audit rows through the use case into a real Oracle. Runs only with {@code -Dintegration=true}. */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class AuditLogIT {

    @DynamicPropertySource
    static void oracle(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", OracleTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> "azs_app");
        registry.add("spring.datasource.password", () -> "test");
        TestKeys.register(registry);
    }

    @Autowired
    RecordAuditUseCase audit;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void recordedRowIsStoredWithItsFields() {
        UUID entityId = UUID.randomUUID();

        tx.executeWithoutResult(s -> audit.record(new AuditEntry(UUID.randomUUID(), "ali", "BRANCH", "REQUEST",
                entityId, "LOCK", "PENDING_BRANCH_REVIEW", "UNDER_REVIEW", "تست", "10.0.0.1", "curl", "corr-it")));

        var row = jdbc.queryForMap("SELECT action, user_name, reason, new_value, correlation_id "
                + "FROM azs_audit.audit_log WHERE entity_id = ?", (Object) toRaw(entityId));
        assertThat(row).containsEntry("ACTION", "LOCK").containsEntry("USER_NAME", "ali")
                .containsEntry("REASON", "تست").containsEntry("CORRELATION_ID", "corr-it");
    }

    @Test
    void auditRowRollsBackWithTheBusinessTransaction() {
        UUID entityId = UUID.randomUUID();

        tx.executeWithoutResult(s -> {
            audit.record(AuditEntry.system("REQUEST", entityId, "AUTO_RELEASE", null));
            s.setRollbackOnly();
        });

        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM azs_audit.audit_log WHERE entity_id = ?",
                Integer.class, (Object) toRaw(entityId));
        assertThat(n).isZero();
    }

    private static byte[] toRaw(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
    }
}
