package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.CancelOutcome;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestBusyException;
import com.sb24.asanzeman.request.domain.model.RequestNotCancellableException;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestTest;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.model.RevocationNotAvailableException;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestResult;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class CancelRequestServiceTest {

    // 2026-11-21 10:00 UTC is 13:30 in Tehran: 1405/08/30
    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final UUID AGENCY = UUID.randomUUID();

    @Mock
    private RequestRepositoryPort requests;
    @Mock
    private CustomerNotifierPort notifier;
    @Mock
    private RecordAuditUseCase audit;

    private CancelRequestService service;

    @BeforeEach
    void setUp() {
        service = new CancelRequestService(requests, notifier, audit, new MutableClock(NOW));
    }

    private static Request request(UUID agency, RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, RequestType.ISSUANCE, status, agency, "0123",
                Rials.of(1000), LocalDate.of(2027, 1, 20), RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"),
                List.of(), "Sky Travel", NOW);
    }

    private static CancelRequestCommand command(Request request) {
        return new CancelRequestCommand(AGENCY, "Sky Travel", request.id(), "10.0.0.5", "Firefox");
    }

    @Test
    void cancelsBeforeTheBranchStartedTellsTheCustomerAndAudits() {
        Request request = request(AGENCY, RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.findByIdForUpdate(request.id())).thenReturn(Optional.of(request));

        CancelRequestResult result = service.cancel(command(request));

        assertThat(result).isEqualTo(new CancelRequestResult(request.id(), CancelOutcome.CANCELLED,
                RequestStatus.CANCELLED));
        InOrder order = Mockito.inOrder(requests, notifier, audit);
        order.verify(requests).findByIdForUpdate(request.id());
        order.verify(requests).updateStatus(request.id(), RequestStatus.CANCELLED, NOW);
        order.verify(notifier).requestCancelled(request.id(), "09121234567", "Sky Travel", "1405/08/30");
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        order.verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::userId, AuditEntry::userRole, AuditEntry::entityType,
                AuditEntry::entityId, AuditEntry::action, AuditEntry::oldValue, AuditEntry::newValue,
                AuditEntry::ipAddress, AuditEntry::userAgent).containsExactly(AGENCY, "AGENCY", "REQUEST",
                request.id(), "CANCEL", "REGISTERED_AT_AGENCY", "CANCELLED", "10.0.0.5", "Firefox");
    }

    @Test
    void anotherAgencysRequestLooksLikeNoRequestAtAll() {
        Request request = request(UUID.randomUUID(), RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.findByIdForUpdate(request.id())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(command(request))).isInstanceOf(RequestNotFoundException.class);
        verifyNoInteractions(notifier, audit);
        verify(requests).findByIdForUpdate(request.id());
        verifyNoMoreInteractions(requests);
    }

    @Test
    void anUnknownRequestIsNotFound() {
        UUID id = UUID.randomUUID();
        when(requests.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(new CancelRequestCommand(AGENCY, "Sky", id, null, null)))
                .isInstanceOf(RequestNotFoundException.class);
    }

    @Test
    void aRequestTheBranchHasStartedNeedsARevocationAndNothingChanges() {
        Request request = request(AGENCY, RequestStatus.UNDER_REVIEW);
        when(requests.findByIdForUpdate(request.id())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(command(request)))
                .isInstanceOf(RevocationNotAvailableException.class);
        verifyNoInteractions(notifier, audit);
        verify(requests).findByIdForUpdate(request.id());
        verifyNoMoreInteractions(requests);
    }

    @Test
    void aFinishedRequestCannotBeCancelledAgain() {
        Request request = request(AGENCY, RequestStatus.CANCELLED);
        when(requests.findByIdForUpdate(request.id())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.cancel(command(request)))
                .isInstanceOf(RequestNotCancellableException.class);
        verifyNoInteractions(notifier, audit);
    }

    @Test
    void aRowAnotherUserHoldsIsABusyConflict() {
        UUID id = UUID.randomUUID();
        doThrow(new RequestBusyException()).when(requests).findByIdForUpdate(id);

        assertThatThrownBy(() -> service.cancel(new CancelRequestCommand(AGENCY, "Sky", id, null, null)))
                .isInstanceOf(RequestBusyException.class);
        verifyNoInteractions(notifier, audit);
    }

    @Test
    void theWholeActionIsOneTransaction() {
        assertThat(CancelRequestService.class.getAnnotation(Transactional.class)).isNotNull();
    }
}
