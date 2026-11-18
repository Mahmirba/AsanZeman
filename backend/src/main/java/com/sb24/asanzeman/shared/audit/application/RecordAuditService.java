package com.sb24.asanzeman.shared.audit.application;

import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.audit.domain.port.out.AuditLogPort;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link Propagation#MANDATORY}: an audit row is written inside the business transaction it describes. Calling this
 * without a transaction is a programming error and fails loudly instead of auditing something that may roll back.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RecordAuditService implements RecordAuditUseCase {

    private final AuditLogPort auditLog;

    public RecordAuditService(AuditLogPort auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void record(AuditEntry entry) {
        auditLog.append(Objects.requireNonNull(entry, "entry"));
    }
}
