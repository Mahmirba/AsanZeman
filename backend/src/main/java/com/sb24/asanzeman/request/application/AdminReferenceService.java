package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.auth.domain.port.in.RevokeSessionsUseCase;
import com.sb24.asanzeman.request.domain.model.AdminAgency;
import com.sb24.asanzeman.request.domain.model.AdminItemNotFoundException;
import com.sb24.asanzeman.request.domain.model.AdminReason;
import com.sb24.asanzeman.request.domain.model.AgencyData;
import com.sb24.asanzeman.request.domain.model.ReasonData;
import com.sb24.asanzeman.request.domain.port.in.AdminActor;
import com.sb24.asanzeman.request.domain.port.in.AdminReferenceUseCase;
import com.sb24.asanzeman.request.domain.port.out.AdminReferencePort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agencies and rejection reasons for the admin. Each change and its audit entry are one transaction. Switching an agency off also ends its sessions. The tables are
 * small and read straight from the database, so a change is seen by every instance at once and no cache needs clearing.
 */
@Service
@Transactional
public class AdminReferenceService implements AdminReferenceUseCase {

    static final String AGENCY = "AGENCY";
    static final String REASON = "REJECTION_REASON";

    private final AdminReferencePort reference;
    private final RecordAuditUseCase audit;
    private final Clock clock;
    private final RevokeSessionsUseCase sessions;

    public AdminReferenceService(AdminReferencePort reference, RecordAuditUseCase audit, Clock clock,
            RevokeSessionsUseCase sessions) {
        this.sessions = sessions;
        this.reference = reference;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminAgency> agencies() {
        return reference.agencies();
    }

    @Override
    public AdminAgency createAgency(AdminActor actor, AgencyData data) {
        UUID id = UUID.randomUUID();
        reference.insertAgency(id, data, actor.name(), clock.instant());
        record(actor, AGENCY, id, "CREATE", null, data.toString());
        return new AdminAgency(id, data);
    }

    @Override
    public AdminAgency updateAgency(AdminActor actor, UUID id, AgencyData data) {
        AdminAgency before = reference.findAgency(id).orElseThrow(AdminItemNotFoundException::new);
        reference.updateAgency(id, data, actor.name(), clock.instant());
        record(actor, AGENCY, id, "UPDATE", before.data().toString(), data.toString());
        if (before.data().active() && !data.active()) {
            // a switched-off agency is logged out of every session at once, not after its 8-hour refresh tokens
            sessions.revokeAllOf(id);
        }
        return new AdminAgency(id, data);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminReason> reasons() {
        return reference.reasons();
    }

    @Override
    public AdminReason createReason(AdminActor actor, ReasonData data) {
        UUID id = UUID.randomUUID();
        reference.insertReason(id, data, actor.name(), clock.instant());
        record(actor, REASON, id, "CREATE", null, data.toString());
        return new AdminReason(id, data);
    }

    @Override
    public AdminReason updateReason(AdminActor actor, UUID id, ReasonData data) {
        AdminReason before = reference.findReason(id).orElseThrow(AdminItemNotFoundException::new);
        reference.updateReason(id, data, actor.name(), clock.instant());
        record(actor, REASON, id, "UPDATE", before.data().toString(), data.toString());
        return new AdminReason(id, data);
    }

    private void record(AdminActor actor, String entity, UUID id, String action, String from, String to) {
        audit.record(new AuditEntry(actor.id(), actor.name(), "ADMIN", entity, id, action, from, to, null,
                actor.clientIp(), actor.userAgent(), null));
    }
}
