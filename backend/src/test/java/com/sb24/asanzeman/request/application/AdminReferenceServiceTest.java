package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.AdminAgency;
import com.sb24.asanzeman.request.domain.model.AdminItemNotFoundException;
import com.sb24.asanzeman.request.domain.model.AdminReason;
import com.sb24.asanzeman.request.domain.model.AgencyData;
import com.sb24.asanzeman.request.domain.model.AgencyDuplicateException;
import com.sb24.asanzeman.request.domain.model.ReasonData;
import com.sb24.asanzeman.request.domain.port.in.AdminActor;
import com.sb24.asanzeman.request.domain.port.out.AdminReferencePort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AdminReferenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final AdminActor ADMIN = new AdminActor(UUID.randomUUID(), "root", "10.0.0.9", "Firefox");
    private static final AgencyData AGENCY = new AgencyData("9001", "0042", "Sky Travel", true, "09121234567");
    private static final ReasonData REASON = new ReasonData("ISSUANCE", "Incomplete", true, 1);

    @Mock
    private AdminReferencePort reference;
    @Mock
    private RecordAuditUseCase audit;
    @Mock
    private com.sb24.asanzeman.auth.domain.port.in.RevokeSessionsUseCase sessions;

    private AdminReferenceService service;

    @BeforeEach
    void setUp() {
        service = new AdminReferenceService(reference, audit, new MutableClock(NOW), sessions);
    }

    private AuditEntry audited() {
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        return entry.getValue();
    }

    @Test
    void listsAreReadStraightFromTheTables() {
        var agencies = List.of(new AdminAgency(UUID.randomUUID(), AGENCY));
        var reasons = List.of(new AdminReason(UUID.randomUUID(), REASON));
        when(reference.agencies()).thenReturn(agencies);
        when(reference.reasons()).thenReturn(reasons);

        assertThat(service.agencies()).isEqualTo(agencies);
        assertThat(service.reasons()).isEqualTo(reasons);
    }

    @Test
    void createsAnAgencyAndAuditsIt() {
        AdminAgency created = service.createAgency(ADMIN, AGENCY);

        assertThat(created.data()).isEqualTo(AGENCY);
        verify(reference).insertAgency(created.id(), AGENCY, "root", NOW);
        assertThat(audited()).extracting(AuditEntry::userId, AuditEntry::userRole, AuditEntry::entityType,
                AuditEntry::entityId, AuditEntry::action, AuditEntry::oldValue, AuditEntry::ipAddress,
                AuditEntry::userAgent).containsExactly(ADMIN.id(), "ADMIN", "AGENCY", created.id(), "CREATE", null,
                "10.0.0.9", "Firefox");
    }

    @Test
    void aTakenAgencyIsRefusedAndNotAudited() {
        org.mockito.Mockito.doThrow(new AgencyDuplicateException()).when(reference)
                .insertAgency(any(), eq(AGENCY), any(), any());

        assertThatThrownBy(() -> service.createAgency(ADMIN, AGENCY)).isInstanceOf(AgencyDuplicateException.class);
        verifyNoInteractions(audit);
    }

    @Test
    void updatesAnAgencyAndAuditsTheOldAndTheNewValue() {
        UUID id = UUID.randomUUID();
        var before = new AgencyData("9001", "0042", "Old name", true, null);
        when(reference.findAgency(id)).thenReturn(Optional.of(new AdminAgency(id, before)));

        service.updateAgency(ADMIN, id, AGENCY);

        verify(reference).updateAgency(id, AGENCY, "root", NOW);
        assertThat(audited()).extracting(AuditEntry::action, AuditEntry::entityId, AuditEntry::oldValue,
                AuditEntry::newValue).containsExactly("UPDATE", id, before.toString(), AGENCY.toString());
    }

    @Test
    void switchingAnAgencyOffEndsItsSessionsButOtherChangesDoNot() {
        UUID id = UUID.randomUUID();
        var on = new AgencyData("9001", "0042", "Sky", true, null);
        var off = new AgencyData("9001", "0042", "Sky", false, null);
        when(reference.findAgency(id)).thenReturn(Optional.of(new AdminAgency(id, on)));

        service.updateAgency(ADMIN, id, new AgencyData("9001", "0042", "Renamed", true, null));
        verifyNoInteractions(sessions);

        service.updateAgency(ADMIN, id, off);
        verify(sessions).revokeAllOf(id);

        when(reference.findAgency(id)).thenReturn(Optional.of(new AdminAgency(id, off)));
        service.updateAgency(ADMIN, id, off); // already off: nothing new to end
        service.updateAgency(ADMIN, id, on); // switched back on: nothing to end
        verify(sessions, org.mockito.Mockito.times(1)).revokeAllOf(id);
    }

    @Test
    void anUnknownAgencyOrReasonIsNotFoundAndNothingChanges() {
        UUID id = UUID.randomUUID();
        when(reference.findAgency(id)).thenReturn(Optional.empty());
        when(reference.findReason(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAgency(ADMIN, id, AGENCY))
                .isInstanceOf(AdminItemNotFoundException.class);
        assertThatThrownBy(() -> service.updateReason(ADMIN, id, REASON))
                .isInstanceOf(AdminItemNotFoundException.class);
        verify(reference, never()).updateAgency(any(), any(), any(), any());
        verify(reference, never()).updateReason(any(), any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void createsAReasonAndAuditsIt() {
        AdminReason created = service.createReason(ADMIN, REASON);

        verify(reference).insertReason(created.id(), REASON, "root", NOW);
        assertThat(audited()).extracting(AuditEntry::entityType, AuditEntry::action, AuditEntry::newValue)
                .containsExactly("REJECTION_REASON", "CREATE", REASON.toString());
    }

    @Test
    void updatesAReasonAndAuditsTheOldAndTheNewValue() {
        UUID id = UUID.randomUUID();
        var before = new ReasonData("ISSUANCE", "Old", true, 1);
        when(reference.findReason(id)).thenReturn(Optional.of(new AdminReason(id, before)));
        var off = new ReasonData("ISSUANCE", "Old", false, 1);

        service.updateReason(ADMIN, id, off);

        verify(reference).updateReason(id, off, "root", NOW);
        assertThat(audited()).extracting(AuditEntry::action, AuditEntry::oldValue, AuditEntry::newValue)
                .containsExactly("UPDATE", before.toString(), off.toString());
    }

    @Test
    void theWholeActionIsOneTransaction() {
        assertThat(AdminReferenceService.class.getAnnotation(Transactional.class)).isNotNull();
    }
}
