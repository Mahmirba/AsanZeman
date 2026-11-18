package com.sb24.asanzeman.shared.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.out.AuditLogPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RecordAuditServiceTest {

    @Mock
    private AuditLogPort auditLog;

    @Test
    void appendsTheEntryToTheLog() {
        AuditEntry entry = AuditEntry.system("REQUEST", UUID.randomUUID(), "AUTO_RELEASE", null);

        new RecordAuditService(auditLog).record(entry);

        verify(auditLog).append(entry);
    }

    @Test
    void rejectsANullEntryWithoutTouchingTheLog() {
        RecordAuditService service = new RecordAuditService(auditLog);

        assertThatThrownBy(() -> service.record(null)).isInstanceOf(NullPointerException.class)
                .hasMessage("entry");
        verifyNoInteractions(auditLog);
    }

    @Test
    void mustRunInsideTheBusinessTransaction() {
        Transactional tx = RecordAuditService.class.getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
