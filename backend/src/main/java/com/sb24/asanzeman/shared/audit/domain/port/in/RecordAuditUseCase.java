package com.sb24.asanzeman.shared.audit.domain.port.in;

import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;

/** Writes one audit row. Joins the caller's transaction, so the audit row and the change commit together. */
public interface RecordAuditUseCase {

    void record(AuditEntry entry);
}
