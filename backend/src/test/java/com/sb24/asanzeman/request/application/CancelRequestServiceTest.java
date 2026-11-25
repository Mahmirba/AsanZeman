package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import com.sb24.asanzeman.request.domain.model.AgencyNotActiveException;
import com.sb24.asanzeman.request.domain.model.CancelOutcome;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestBusyException;
import com.sb24.asanzeman.request.domain.model.RequestNotCancellableException;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestTest;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.model.Review;
import com.sb24.asanzeman.request.domain.model.ReviewLock;
import com.sb24.asanzeman.request.domain.model.RevocationAlreadyRequestedException;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestResult;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.ReviewRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    private static final TrackingCode REVOCATION_CODE = TrackingCode.of("0123", "0042", JalaliDate.of(1405, 8, 30), 7);

    @Mock
    private ReviewRepositoryPort reviews;
    @Mock
    private RequestRepositoryPort requests;
    @Mock
    private AgencyRegistryPort agencies;
    @Mock
    private TrackingCodeGenerator trackingCodes;
    @Mock
    private CustomerNotifierPort notifier;
    @Mock
    private RecordAuditUseCase audit;

    private CancelRequestService service;

    @BeforeEach
    void setUp() {
        service = new CancelRequestService(reviews, requests, agencies, trackingCodes, notifier, audit,
                new MutableClock(NOW));
    }

    private static Request request(UUID agency, RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, RequestType.ISSUANCE, status, agency, "0123",
                Rials.of(1000), LocalDate.of(2027, 1, 20), RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"),
                List.of(), "Sky Travel", NOW);
    }

    private static CancelRequestCommand command(Request request) {
        return new CancelRequestCommand(AGENCY, "Sky Travel", request.id(), "10.0.0.5", "Firefox");
    }

    private Review found(Request request, ReviewLock lock, boolean revocationOpen) {
        var review = new Review(request, lock, "0123", null, revocationOpen);
        when(reviews.findForUpdate(request.id())).thenReturn(Optional.of(review));
        return review;
    }

    // ---- before «اقدام»: cancel ------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"REGISTERED_AT_AGENCY", "PENDING_BRANCH_REVIEW"})
    void cancelsBeforeTheBranchStartedTellsTheCustomerAndAudits(RequestStatus status) {
        Request request = request(AGENCY, status);
        found(request, null, false);

        CancelRequestResult result = service.cancel(command(request));

        assertThat(result).isEqualTo(new CancelRequestResult(request.id(), CancelOutcome.CANCELLED,
                RequestStatus.CANCELLED));
        InOrder order = Mockito.inOrder(reviews, requests, notifier, audit);
        order.verify(reviews).findForUpdate(request.id());
        order.verify(requests).updateStatus(request.id(), RequestStatus.CANCELLED, NOW);
        order.verify(notifier).requestCancelled(request.id(), "09121234567", "Sky Travel", "1405/08/30");
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        order.verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::userId, AuditEntry::userRole, AuditEntry::entityType,
                AuditEntry::entityId, AuditEntry::action, AuditEntry::oldValue, AuditEntry::newValue,
                AuditEntry::ipAddress, AuditEntry::userAgent).containsExactly(AGENCY, "AGENCY", "REQUEST",
                request.id(), "CANCEL", status.name(), "CANCELLED", "10.0.0.5", "Firefox");
        verify(reviews, never()).insertRevocation(any(), any(), any());
    }

    @Test
    void anotherAgencysRequestLooksLikeNoRequestAtAll() {
        Request request = request(UUID.randomUUID(), RequestStatus.REGISTERED_AT_AGENCY);
        found(request, null, false);

        assertThatThrownBy(() -> service.cancel(command(request))).isInstanceOf(RequestNotFoundException.class);
        verifyNoInteractions(notifier, audit, requests);
    }

    @Test
    void anUnknownRequestIsNotFound() {
        UUID id = UUID.randomUUID();
        when(reviews.findForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(new CancelRequestCommand(AGENCY, "Sky", id, null, null)))
                .isInstanceOf(RequestNotFoundException.class);
    }

    @Test
    void aRowAnotherUserHoldsIsABusyConflict() {
        UUID id = UUID.randomUUID();
        doThrow(new RequestBusyException()).when(reviews).findForUpdate(id);

        assertThatThrownBy(() -> service.cancel(new CancelRequestCommand(AGENCY, "Sky", id, null, null)))
                .isInstanceOf(RequestBusyException.class);
        verifyNoInteractions(notifier, audit);
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"REJECTED", "CANCELLED", "REVOKED", "COMPLETED"})
    void aFinishedRequestHasNothingToCancel(RequestStatus status) {
        Request request = request(AGENCY, status);
        found(request, null, false);

        assertThatThrownBy(() -> service.cancel(command(request)))
                .isInstanceOf(RequestNotCancellableException.class);
        verifyNoInteractions(notifier, audit);
        verify(reviews, never()).insertRevocation(any(), any(), any());
    }

    // ---- from «اقدام» on: a revocation request --------------------------------------------------------------

    private void agencyIsActive() {
        when(agencies.find(AGENCY)).thenReturn(Optional.of(new AgencyInfo(AGENCY, "0042", "Sky Travel", true)));
        when(trackingCodes.next("0123", "0042")).thenReturn(REVOCATION_CODE);
    }

    @Test
    void anIssuedRequestGetsARevocationRequestInTheBranchQueue() {
        Request request = request(AGENCY, RequestStatus.ISSUED);
        found(request, null, false);
        agencyIsActive();

        CancelRequestResult result = service.cancel(command(request));

        assertThat(result.outcome()).isEqualTo(CancelOutcome.REVOCATION_REQUESTED);
        assertThat(result.status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        var revocation = ArgumentCaptor.forClass(Request.class);
        verify(reviews).insertRevocation(revocation.capture(), eq(request.id()), eq(null));
        assertThat(result.requestId()).isEqualTo(revocation.getValue().id()).isNotEqualTo(request.id());
        assertThat(revocation.getValue()).extracting(Request::type, Request::status, Request::trackingCode,
                Request::registeredBy, Request::branchCode, Request::agencyId, Request::amount)
                .containsExactly(RequestType.REVOCATION, RequestStatus.PENDING_BRANCH_REVIEW, REVOCATION_CODE,
                        "Sky Travel", "0123", AGENCY, Rials.of(1000));
        assertThat(revocation.getValue().customer()).isEqualTo(request.customer());
        verify(requests, never()).updateStatus(any(), any(), any());
        verifyNoInteractions(notifier);
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::entityId, AuditEntry::userRole,
                AuditEntry::reason).containsExactly("REQUEST_REVOCATION", request.id(), "AGENCY",
                "revocation " + REVOCATION_CODE.value());
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"UNDER_REVIEW", "UNDER_ACTION"})
    void aRequestSomeoneIsWorkingOnSendsItsRevocationStraightToThatUser(RequestStatus status) {
        Request request = request(AGENCY, status);
        UUID holder = UUID.randomUUID();
        found(request, new ReviewLock(holder, "ali.b", NOW.minusSeconds(3600), NOW.minusSeconds(60)), false);
        agencyIsActive();

        CancelRequestResult result = service.cancel(command(request));

        assertThat(result.outcome()).isEqualTo(CancelOutcome.REVOCATION_REQUESTED);
        assertThat(result.status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        var lock = ArgumentCaptor.forClass(ReviewLock.class);
        var revocation = ArgumentCaptor.forClass(Request.class);
        verify(reviews).insertRevocation(revocation.capture(), eq(request.id()), lock.capture());
        assertThat(revocation.getValue().status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(lock.getValue()).isEqualTo(new ReviewLock(holder, "ali.b", NOW, NOW)); // theirs from now on
    }

    @Test
    void theAuditNamesThePersonWhoActedForTheAgencyWhileTheSmsKeepsTheAgencyName() {
        Request cancelled = request(AGENCY, RequestStatus.REGISTERED_AT_AGENCY);
        Request issued = request(AGENCY, RequestStatus.ISSUED);
        found(cancelled, null, false);
        found(issued, null, false);
        agencyIsActive();

        service.cancel(new CancelRequestCommand(AGENCY, "Sky Travel", cancelled.id(), "10.0.0.5", "Firefox", "ali.sky"));
        service.cancel(new CancelRequestCommand(AGENCY, "Sky Travel", issued.id(), "10.0.0.5", "Firefox", "ali.sky"));

        verify(notifier).requestCancelled(cancelled.id(), "09121234567", "Sky Travel", "1405/08/30");
        var entries = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit, Mockito.times(2)).record(entries.capture());
        assertThat(entries.getAllValues()).extracting(AuditEntry::userName).containsExactly("ali.sky", "ali.sky");
        var revocation = ArgumentCaptor.forClass(Request.class);
        verify(reviews).insertRevocation(revocation.capture(), eq(issued.id()), eq(null));
        assertThat(revocation.getValue().registeredBy()).isEqualTo("ali.sky");
    }

    @Test
    void aSecondRevocationWhileOneIsOpenIsRefused() {
        Request request = request(AGENCY, RequestStatus.ISSUED);
        found(request, null, true);

        assertThatThrownBy(() -> service.cancel(command(request)))
                .isInstanceOf(RevocationAlreadyRequestedException.class);
        verify(reviews, never()).insertRevocation(any(), any(), any());
        verifyNoInteractions(audit, trackingCodes);
    }

    @Test
    void anAgencyThatIsNoLongerActiveCannotRequestARevocation() {
        Request request = request(AGENCY, RequestStatus.ISSUED);
        found(request, null, false);
        when(agencies.find(AGENCY)).thenReturn(Optional.of(new AgencyInfo(AGENCY, "0042", "Sky Travel", false)));

        assertThatThrownBy(() -> service.cancel(command(request))).isInstanceOf(AgencyNotActiveException.class);
        verify(reviews, never()).insertRevocation(any(), any(), any());
    }

    @Test
    void aRevocationRequestItselfCannotBeCancelled() {
        Request issuance = request(AGENCY, RequestStatus.ISSUED);
        Request revocation = issuance.requestRevocation(UUID.randomUUID(), REVOCATION_CODE,
                RequestStatus.PENDING_BRANCH_REVIEW, "Sky Travel", NOW);
        found(revocation, null, false);

        assertThatThrownBy(() -> service.cancel(command(revocation)))
                .isInstanceOf(RequestNotCancellableException.class);
    }

    @Test
    void theWholeActionIsOneTransaction() {
        assertThat(CancelRequestService.class.getAnnotation(Transactional.class)).isNotNull();
    }
}
