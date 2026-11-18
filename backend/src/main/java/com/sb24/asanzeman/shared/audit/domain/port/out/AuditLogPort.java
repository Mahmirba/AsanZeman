package com.sb24.asanzeman.shared.audit.domain.port.out;

import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;

/** Append-only store for audit rows. There is deliberately no update or delete (6-5). */
public interface AuditLogPort {

    void append(AuditEntry entry);
}
