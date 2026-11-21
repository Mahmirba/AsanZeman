package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.BranchData;
import com.sb24.asanzeman.request.domain.model.BranchSyncResult;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.BranchSourcePort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BranchSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T02:00:00Z");

    @Mock
    private BranchSourcePort source;
    @Mock
    private BranchRepositoryPort branches;
    @Mock
    private RecordAuditUseCase audit;

    private BranchSyncService service;

    @BeforeEach
    void setUp() {
        service = new BranchSyncService(source, branches, audit, new MutableClock(NOW));
    }

    private static BranchData branch(String code) {
        return new BranchData(code, "Branch " + code, null, null, null, null, null);
    }

    @Test
    void appliesTheFetchedBranchesAndAuditsTheSummary() {
        List<BranchData> fetched = List.of(branch("0123"), branch("0456"));
        when(source.fetchAll()).thenReturn(fetched);
        when(branches.apply(fetched, NOW)).thenReturn(new BranchSyncResult(1, 1, 0));

        BranchSyncResult result = service.sync();

        assertThat(result).isEqualTo(new BranchSyncResult(1, 1, 0));
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::entityType, AuditEntry::action, AuditEntry::reason,
                AuditEntry::userRole).containsExactly("BRANCH", "SYNC", "inserted 1, updated 1, deactivated 0",
                "SYSTEM");
    }

    @Test
    void anEmptyAnswerIsRefusedSoNoBranchIsDeactivated() {
        when(source.fetchAll()).thenReturn(List.of());

        assertThatThrownBy(service::sync).isInstanceOf(TemporarilyUnavailableException.class);
        verifyNoInteractions(branches, audit);
    }

    @Test
    void aRepeatedBranchCodeIsRefused() {
        when(source.fetchAll()).thenReturn(List.of(branch("0123"), branch("0123")));

        assertThatThrownBy(service::sync).isInstanceOf(TemporarilyUnavailableException.class);
        verifyNoInteractions(branches, audit);
    }

    @Test
    void aFailureAtYaghutChangesNothing() {
        var down = new TemporarilyUnavailableException("Yaghut", new RuntimeException("timeout"));
        when(source.fetchAll()).thenThrow(down);

        assertThatThrownBy(service::sync).isSameAs(down);
        verifyNoInteractions(branches, audit);
    }

    @Test
    void hasBranchesAsksTheTable() {
        when(branches.any()).thenReturn(true, false);

        assertThat(service.hasBranches()).isTrue();
        assertThat(service.hasBranches()).isFalse();
        verify(branches, org.mockito.Mockito.times(2)).any();
        verifyNoInteractions(source);
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.never()).record(any());
    }
}
