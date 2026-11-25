package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestBusyException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestTest;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class ExpireIncompleteRequestsServiceTest {

    // 2026-11-23 10:00 UTC is 13:30 in Tehran: 1405/09/02
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @Mock
    private RequestRepositoryPort requests;
    @Mock
    private AgencyRegistryPort agencies;
    @Mock
    private CustomerNotifierPort notifier;
    @Mock
    private RecordAuditUseCase audit;
    @Mock
    private PlatformTransactionManager transactions;

    private ExpireIncompleteRequestsService service;

    @BeforeEach
    void setUp() {
        service = new ExpireIncompleteRequestsService(requests, agencies, notifier, audit, new MutableClock(NOW),
                new CompletionSettings(Duration.ofDays(7)), transactions);
    }

    private static Request request(RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, RequestType.ISSUANCE, status, UUID.randomUUID(),
                "0123", Rials.of(1000), LocalDate.of(2027, 1, 20),
                RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"), List.of(), "Sky Travel", NOW);
    }

    @Test
    void aRequestNotCompletedInSevenDaysIsRejectedAuditedAndTheCustomerTold() {
        Request request = request(RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.incompleteSince(NOW.minus(Duration.ofDays(7)))).thenReturn(List.of(request.id()));
        when(requests.findByIdForUpdate(request.id())).thenReturn(Optional.of(request));
        when(agencies.find(request.agencyId())).thenReturn(
                Optional.of(new AgencyInfo(request.agencyId(), "0042", "Sky Travel", true)));

        assertThat(service.expireIncomplete()).isEqualTo(1);

        verify(requests).updateStatus(request.id(), RequestStatus.REJECTED, NOW);
        verify(notifier).requestExpired(request.id(), "09121234567", "Sky Travel", "1405/09/02");
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::userRole, AuditEntry::action, AuditEntry::oldValue,
                AuditEntry::newValue, AuditEntry::reason).containsExactly("SYSTEM", "AUTO_REJECT",
                "REGISTERED_AT_AGENCY", "REJECTED", "not completed within PT168H");
    }

    @Test
    void aRequestThatMovedOnMeanwhileIsLeftAloneAndAnUnknownOneIsSkipped() {
        Request submitted = request(RequestStatus.PENDING_BRANCH_REVIEW);
        UUID gone = UUID.randomUUID();
        when(requests.incompleteSince(any())).thenReturn(List.of(submitted.id(), gone));
        when(requests.findByIdForUpdate(submitted.id())).thenReturn(Optional.of(submitted));
        when(requests.findByIdForUpdate(gone)).thenReturn(Optional.empty());

        assertThat(service.expireIncomplete()).isZero();

        verify(requests, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(notifier, audit);
    }

    @Test
    void aBusyRequestWaitsForTheNextRunAndTheOthersAreStillRejected() {
        Request busy = request(RequestStatus.REGISTERED_AT_AGENCY);
        Request free = request(RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.incompleteSince(any())).thenReturn(List.of(busy.id(), free.id()));
        doThrow(new RequestBusyException()).when(requests).findByIdForUpdate(busy.id());
        when(requests.findByIdForUpdate(free.id())).thenReturn(Optional.of(free));
        when(agencies.find(free.agencyId())).thenReturn(Optional.empty());

        assertThat(service.expireIncomplete()).isEqualTo(1);

        verify(requests).updateStatus(free.id(), RequestStatus.REJECTED, NOW);
        verify(requests, never()).updateStatus(busy.id(), RequestStatus.REJECTED, NOW);
        verify(notifier).requestExpired(free.id(), "09121234567", "", "1405/09/02");
    }

    @Test
    void nothingToDoWhenNoRequestIsOverdue() {
        when(requests.incompleteSince(any())).thenReturn(List.of());

        assertThat(service.expireIncomplete()).isZero();
        verifyNoInteractions(notifier, audit);
    }

    @Test
    void aRequestThatFailsIsLoggedAndTheOthersAreStillRejected() {
        Request broken = request(RequestStatus.REGISTERED_AT_AGENCY);
        Request fine = request(RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.incompleteSince(any())).thenReturn(List.of(broken.id(), fine.id()));
        when(requests.findByIdForUpdate(broken.id())).thenReturn(Optional.of(broken));
        when(requests.findByIdForUpdate(fine.id())).thenReturn(Optional.of(fine));
        when(agencies.find(any())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("outbox down")).when(notifier)
                .requestExpired(ArgumentMatchers.eq(broken.id()), any(), any(), any());

        assertThat(service.expireIncomplete()).isEqualTo(1);

        verify(notifier).requestExpired(ArgumentMatchers.eq(fine.id()), any(), any(), any());
    }

    @Test
    void eachRequestRunsInItsOwnTransaction() {
        Request a = request(RequestStatus.REGISTERED_AT_AGENCY);
        Request b = request(RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.incompleteSince(any())).thenReturn(List.of(a.id(), b.id()));
        when(requests.findByIdForUpdate(a.id())).thenReturn(Optional.of(a));
        when(requests.findByIdForUpdate(b.id())).thenReturn(Optional.of(b));
        when(agencies.find(any())).thenReturn(Optional.empty());

        service.expireIncomplete();

        verify(transactions, Mockito.times(2)).getTransaction(any());
        verify(transactions, Mockito.times(2)).commit(any());
    }
}
