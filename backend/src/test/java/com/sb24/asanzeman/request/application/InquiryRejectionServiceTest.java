package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict.Decision;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunFinished;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestBusyException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestTest;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
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
class InquiryRejectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @Mock
    private RequestRepositoryPort requests;
    @Mock
    private com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort agencies;
    @Mock
    private com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort notifier;
    @Mock
    private RecordAuditUseCase audit;

    private InquiryRejectionService service;

    @BeforeEach
    void setUp() {
        service = new InquiryRejectionService(requests, agencies, notifier, audit, new MutableClock(NOW));
    }

    private static Request request(RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, RequestType.ISSUANCE, status, UUID.randomUUID(),
                "0123", Rials.of(1000), LocalDate.of(2027, 1, 20),
                RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"), List.of(), "Sky Travel", NOW);
    }

    private static InquiryRunFinished finished(Request request, Decision decision) {
        return new InquiryRunFinished(request.id(), UUID.randomUUID(), decision,
                decision == Decision.REJECT ? "inquiry.blacklist.negative" : null);
    }

    @Test
    void aRejectionRejectsTheRequestAndIsAudited() {
        Request request = request(RequestStatus.REGISTERED_AT_AGENCY);
        when(agencies.find(request.agencyId())).thenReturn(Optional.of(
                new com.sb24.asanzeman.request.domain.model.AgencyInfo(request.agencyId(), "0042", "Sky Travel", true)));
        when(requests.findByIdForUpdate(request.id())).thenReturn(Optional.of(request));

        service.on(finished(request, Decision.REJECT));

        verify(requests).updateStatus(request.id(), RequestStatus.REJECTED, NOW);
        verify(notifier).requestRejectedByInquiry(request.id(), "09121234567", "Sky Travel", "1405/09/02");
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::userRole, AuditEntry::reason)
                .containsExactly("REJECT_BY_INQUIRY", "SYSTEM", "inquiry.blacklist.negative");
    }

    @Test
    void aBlockedOrPassedRunChangesNothing() {
        Request request = request(RequestStatus.REGISTERED_AT_AGENCY);

        service.on(finished(request, Decision.BLOCKED));
        service.on(finished(request, Decision.PROCEED));

        verifyNoInteractions(requests, audit, notifier);
    }

    @Test
    void aRequestThatNoLongerWaitsForItsInquiriesIsLeftAlone() {
        Request cancelled = request(RequestStatus.CANCELLED);
        when(requests.findByIdForUpdate(cancelled.id())).thenReturn(Optional.of(cancelled));
        UUID unknown = UUID.randomUUID();
        when(requests.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        service.on(finished(cancelled, Decision.REJECT));
        service.on(new InquiryRunFinished(unknown, UUID.randomUUID(), Decision.REJECT, "k"));

        verify(requests, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(audit, notifier);
    }

    @Test
    void aBusyRequestIsNotRejectedNowButTheNextRunWillRejectItAgain() {
        Request request = request(RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.findByIdForUpdate(request.id())).thenThrow(new RequestBusyException());

        service.on(finished(request, Decision.REJECT));

        verify(requests, never()).updateStatus(eq(request.id()), any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void itRunsInATransactionSoTheStatusAndTheAuditGoTogether() throws Exception {
        var on = InquiryRejectionService.class.getMethod("on", InquiryRunFinished.class);

        assertThat(on.getAnnotation(Transactional.class)).isNotNull();
        assertThat(on.getAnnotation(org.springframework.context.event.EventListener.class)).isNotNull();
    }
}
