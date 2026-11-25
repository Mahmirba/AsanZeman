package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.BranchUser;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.NotYourBranchException;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestBusyException;
import com.sb24.asanzeman.request.domain.model.RequestLockedByOtherException;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestNotReviewableException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestTest;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.model.Review;
import com.sb24.asanzeman.request.domain.model.ReviewLock;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.request.domain.port.in.BranchCommand;
import com.sb24.asanzeman.request.domain.port.in.ReviewResult;
import com.sb24.asanzeman.request.domain.port.out.ReviewRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class BranchReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final BranchUser ALI = new BranchUser(UUID.randomUUID(), "ali.b", "0123", false);
    private static final BranchUser SARA = new BranchUser(UUID.randomUUID(), "sara.b", "0123", false);
    private static final BranchUser ADMIN = new BranchUser(UUID.randomUUID(), "root", "0123", true);
    private static final UUID REASON = UUID.randomUUID();

    @Mock
    private ReviewRepositoryPort reviews;
    @Mock
    private BranchSms sms;
    @Mock
    private RecordAuditUseCase audit;

    private BranchReviewService service;

    @BeforeEach
    void setUp() {
        service = new BranchReviewService(reviews, sms, audit, new MutableClock(NOW),
                new ReviewSettings(Duration.ofMinutes(60)));
    }

    private static Request request(RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, RequestType.ISSUANCE, status, UUID.randomUUID(),
                "0123", Rials.of(1000), LocalDate.of(2027, 1, 20),
                RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"), List.of(), "Sky Travel", NOW);
    }

    private Review found(Request request, BranchUser holder) {
        var review = new Review(request, holder == null ? null
                : new ReviewLock(holder.id(), holder.name(), NOW.minusSeconds(300), NOW.minusSeconds(300)), "0123");
        when(reviews.findForUpdate(request.id())).thenReturn(Optional.of(review));
        return review;
    }

    private static BranchCommand command(BranchUser user, Request request) {
        return new BranchCommand(user, request.id(), "10.0.0.5", "Firefox");
    }

    // ---- «اقدام» -----------------------------------------------------------------------------------------------

    @Test
    void takingARequestSavesTheStatusAndTheLockAndAuditsIt() {
        Request request = request(RequestStatus.PENDING_BRANCH_REVIEW);
        found(request, null);

        ReviewResult result = service.take(command(ALI, request));

        assertThat(result.status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(result.heldBy()).isEqualTo("ali.b");
        assertThat(result.heldSince()).isEqualTo(NOW);
        var lock = ArgumentCaptor.forClass(ReviewLock.class);
        verify(reviews).save(ArgumentMatchers.eq(request.id()),
                ArgumentMatchers.eq(RequestStatus.UNDER_REVIEW), lock.capture(),
                ArgumentMatchers.eq(NOW));
        assertThat(lock.getValue().holderId()).isEqualTo(ALI.id());
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::userRole, AuditEntry::userName,
                AuditEntry::oldValue, AuditEntry::newValue, AuditEntry::ipAddress)
                .containsExactly("LOCK", "BRANCH", "ali.b", "PENDING_BRANCH_REVIEW", "UNDER_REVIEW", "10.0.0.5");
    }

    @Test
    void anUnknownRequestIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(reviews.findForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.take(new BranchCommand(ALI, unknown, null, null)))
                .isInstanceOf(RequestNotFoundException.class);
        verify(reviews, never()).save(any(), any(), any(), any());
    }

    @Test
    void aBusyRowAndAnotherUsersLockAreRefusedAndNothingIsSavedOrAudited() {
        Request busy = request(RequestStatus.PENDING_BRANCH_REVIEW);
        when(reviews.findForUpdate(busy.id())).thenThrow(new RequestBusyException());
        Request held = request(RequestStatus.UNDER_REVIEW);
        found(held, ALI);

        assertThatThrownBy(() -> service.take(command(SARA, busy))).isInstanceOf(RequestBusyException.class);
        assertThatThrownBy(() -> service.take(command(SARA, held))).isInstanceOf(RequestLockedByOtherException.class);
        verify(reviews, never()).save(any(), any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void aUserOfAnotherBranchIsRefused() {
        Request request = request(RequestStatus.PENDING_BRANCH_REVIEW);
        found(request, null);

        assertThatThrownBy(() -> service.take(command(new BranchUser(UUID.randomUUID(), "x", "0999", false), request)))
                .isInstanceOf(NotYourBranchException.class);
    }

    // ---- approve, abandon -----------------------------------------------------------------------------------

    @Test
    void approvingKeepsTheOwnershipAndAbandoningClearsIt() {
        Request review = request(RequestStatus.UNDER_REVIEW);
        found(review, ALI);
        Request action = request(RequestStatus.UNDER_ACTION);
        found(action, ALI);

        ReviewResult approved = service.approve(command(ALI, review));
        ReviewResult abandoned = service.abandon(command(ALI, action));

        assertThat(approved.status()).isEqualTo(RequestStatus.UNDER_ACTION);
        assertThat(approved.heldBy()).isEqualTo("ali.b");
        assertThat(abandoned.status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        assertThat(abandoned.heldBy()).isNull();
        verify(reviews).save(action.id(), RequestStatus.PENDING_BRANCH_REVIEW, null, NOW);
    }

    // ---- reject ---------------------------------------------------------------------------------------------

    @Test
    void rejectingRecordsTheReasonAndTheCommentAndTellsTheCustomer() {
        Request request = request(RequestStatus.UNDER_REVIEW);
        found(request, ALI);
        when(reviews.reasonExists(REASON, "ISSUANCE")).thenReturn(true);

        ReviewResult result = service.reject(command(ALI, request), REASON, "  documents are incomplete ");

        assertThat(result.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(result.heldBy()).isNull();
        verify(reviews).recordRejection(request.id(), REASON, "documents are incomplete");
        verify(sms).issuanceRejected(argThat(r -> r.id().equals(request.id())));
    }

    @Test
    void aRejectionNeedsACommentAndAKnownActiveReasonBeforeAnythingIsTouched() {
        Request request = request(RequestStatus.UNDER_REVIEW);

        for (String comment : new String[] {null, "", "   ", "x".repeat(201)}) {
            assertThatThrownBy(() -> service.reject(command(ALI, request), REASON, comment))
                    .isInstanceOf(InvalidRequestDataException.class);
        }
        verifyNoInteractions(reviews, sms, audit); // a bad comment is refused before the request is even read

        found(request, ALI);
        when(reviews.reasonExists(REASON, "ISSUANCE")).thenReturn(false);
        assertThatThrownBy(() -> service.reject(command(ALI, request), REASON, "fine"))
                .isInstanceOfSatisfying(InvalidRequestDataException.class,
                        e -> assertThat(e.args().get(0).toString()).contains("reason"));
        assertThatThrownBy(() -> service.reject(command(ALI, request), null, "fine"))
                .isInstanceOf(InvalidRequestDataException.class);
        // a wrong reason ends the transaction with nothing saved, audited or sent
        verify(reviews, never()).save(any(), any(), any(), any());
        verify(reviews, never()).recordRejection(any(), any(), any());
        verifyNoInteractions(sms, audit);
    }

    @Test
    void aCommentOfExactlyTwoHundredCharactersIsAccepted() {
        Request request = request(RequestStatus.UNDER_REVIEW);
        found(request, ALI);
        when(reviews.reasonExists(REASON, "ISSUANCE")).thenReturn(true);

        service.reject(command(ALI, request), REASON, "x".repeat(200));

        verify(sms).issuanceRejected(argThat(r -> r.id().equals(request.id())));
    }

    // ---- issue ----------------------------------------------------------------------------------------------

    @Test
    void issuingRecordsTheGuaranteeNumberAndTellsTheCustomer() {
        Request request = request(RequestStatus.UNDER_ACTION);
        found(request, ALI);

        ReviewResult result = service.issue(command(ALI, request), " GT-001/2026 ");

        assertThat(result.status()).isEqualTo(RequestStatus.ISSUED);
        assertThat(result.heldBy()).isNull();
        verify(reviews).recordIssue(request.id(), "GT-001/2026", NOW);
        verify(sms).issued(argThat(r -> r.id().equals(request.id()) && r.status() == RequestStatus.ISSUED));
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::reason)
                .containsExactly("ISSUE", "GT-001/2026");
    }

    @Test
    void aBadGuaranteeNumberIsRefusedBeforeAnythingIsTouched() {
        Request request = request(RequestStatus.UNDER_ACTION);

        assertThatThrownBy(() -> service.issue(command(ALI, request), "12 34"))
                .isInstanceOf(InvalidRequestDataException.class);
        verifyNoInteractions(reviews, sms, audit);
    }

    @Test
    void issuingBeforeTheApprovalIsRefusedAndNothingIsSent() {
        Request request = request(RequestStatus.UNDER_REVIEW);
        found(request, ALI);

        assertThatThrownBy(() -> service.issue(command(ALI, request), "GT-1"))
                .isInstanceOf(RequestNotReviewableException.class);
        verifyNoInteractions(sms);
        verify(reviews, never()).recordIssue(any(), any(), any());
    }

    // ---- revocation (4.2) -----------------------------------------------------------------------------------

    private Request revocation(RequestStatus status) {
        return request(RequestStatus.ISSUED).requestRevocation(UUID.randomUUID(),
                TrackingCode.of("0123", "0042",
                        JalaliDate.of(1405, 9, 2), 9), status, "Sky Travel", NOW);
    }

    private Review foundRevocation(Request revocation, UUID parentId) {
        var review = new Review(revocation, new ReviewLock(ALI.id(), "ali.b", NOW.minusSeconds(300),
                NOW.minusSeconds(300)), "0123", parentId, false);
        when(reviews.findForUpdate(revocation.id())).thenReturn(Optional.of(review));
        return review;
    }

    @Test
    void aRevocationIsRejectedWithAReasonFromTheRevocationList() {
        Request revocation = revocation(RequestStatus.UNDER_REVIEW);
        UUID issuanceId = UUID.randomUUID();
        foundRevocation(revocation, issuanceId);
        when(reviews.reasonExists(REASON, "REVOCATION")).thenReturn(true);

        ReviewResult result = service.reject(command(ALI, revocation), REASON, "still in use");

        assertThat(result.status()).isEqualTo(RequestStatus.REJECTED);
        verify(reviews).recordRejection(revocation.id(), REASON, "still in use");
        verify(sms).revocationRejected(argThat(r -> r.id().equals(revocation.id())), eq(issuanceId));
        verify(sms, never()).issuanceRejected(any());
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo("REJECT_REVOCATION");
    }

    @Test
    void anIssuanceReasonCannotRejectARevocation() {
        Request revocation = revocation(RequestStatus.UNDER_REVIEW);
        foundRevocation(revocation, UUID.randomUUID());
        when(reviews.reasonExists(REASON, "REVOCATION")).thenReturn(false);

        assertThatThrownBy(() -> service.reject(command(ALI, revocation), REASON, "x"))
                .isInstanceOf(InvalidRequestDataException.class);
        verify(reviews, never()).save(any(), any(), any(), any());
        verifyNoInteractions(sms, audit);
    }

    @Test
    void completingARevocationRevokesTheIssuanceAndTellsTheCustomerInOneTransaction() {
        UUID issuanceId = UUID.randomUUID();
        Request revocation = revocation(RequestStatus.UNDER_ACTION);
        foundRevocation(revocation, issuanceId);
        Request issued = new Request(issuanceId, RequestTest.CODE, RequestType.ISSUANCE, RequestStatus.ISSUED,
                revocation.agencyId(), "0123", Rials.of(1000), LocalDate.of(2027, 1, 20), revocation.customer(),
                List.of(), "Sky Travel", NOW);
        when(reviews.findForUpdate(issuanceId)).thenReturn(Optional.of(new Review(issued, null, "0123")));

        ReviewResult result = service.completeRevocation(command(ALI, revocation));

        assertThat(result.status()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(result.heldBy()).isNull();
        verify(reviews).save(revocation.id(), RequestStatus.COMPLETED, null, NOW);
        verify(reviews).save(issuanceId, RequestStatus.REVOKED, null, NOW);
        verify(sms).revoked(argThat(r -> r.id().equals(issuanceId) && r.status() == RequestStatus.REVOKED),
                argThat(r -> r.id().equals(revocation.id()) && r.status() == RequestStatus.COMPLETED));
        var entries = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit, Mockito.times(2)).record(entries.capture());
        assertThat(entries.getAllValues()).extracting(AuditEntry::action, AuditEntry::entityId, AuditEntry::newValue)
                .containsExactly(Tuple.tuple("COMPLETE_REVOCATION", revocation.id(), "COMPLETED"),
                        Tuple.tuple("REVOKED", issuanceId, "REVOKED"));
    }

    @Test
    void anIssuanceWorkedOnWhenItsRevocationCompletesIsRevokedToo() {
        UUID issuanceId = UUID.randomUUID();
        Request revocation = revocation(RequestStatus.UNDER_ACTION);
        foundRevocation(revocation, issuanceId);
        Request working = new Request(issuanceId, RequestTest.CODE, RequestType.ISSUANCE, RequestStatus.UNDER_ACTION,
                revocation.agencyId(), "0123", Rials.of(1000), LocalDate.of(2027, 1, 20), revocation.customer(),
                List.of(), "Sky Travel", NOW);
        when(reviews.findForUpdate(issuanceId)).thenReturn(Optional.of(new Review(working,
                new ReviewLock(ALI.id(), "ali.b", NOW, NOW), "0123", null, true)));

        service.completeRevocation(command(ALI, revocation));

        verify(reviews).save(issuanceId, RequestStatus.REVOKED, null, NOW); // and nobody holds it any more
    }

    @Test
    void aRevocationWithoutAnIssuanceAndAnIssuanceThatCannotBeRevokedAreRefused() {
        Request orphan = revocation(RequestStatus.UNDER_ACTION);
        foundRevocation(orphan, null);
        Request revocation = revocation(RequestStatus.UNDER_ACTION);
        UUID rejectedIssuance = UUID.randomUUID();
        foundRevocation(revocation, rejectedIssuance);
        when(reviews.findForUpdate(rejectedIssuance)).thenReturn(Optional.of(new Review(
                request(RequestStatus.REJECTED), null, "0123")));

        assertThatThrownBy(() -> service.completeRevocation(command(ALI, orphan)))
                .isInstanceOf(RequestNotFoundException.class);
        assertThatThrownBy(() -> service.completeRevocation(command(ALI, revocation)))
                .isInstanceOf(RequestNotReviewableException.class);
        verify(reviews, never()).save(rejectedIssuance, RequestStatus.REVOKED, null, NOW);
    }

    // ---- force release --------------------------------------------------------------------------------------

    @Test
    void anAdminReleasesARequestWhoeverHoldsItWithAReasonThatIsAudited() {
        Request request = request(RequestStatus.UNDER_ACTION);
        found(request, ALI);

        ReviewResult result = service.forceRelease(command(ADMIN, request), " Ali is on leave ");

        assertThat(result.status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::userRole, AuditEntry::reason)
                .containsExactly("FORCE_RELEASE", "ADMIN", "Ali is on leave");
    }

    @Test
    void aReleaseNeedsAReason() {
        Request request = request(RequestStatus.UNDER_ACTION);

        for (String reason : new String[] {null, " ", "x".repeat(201)}) {
            assertThatThrownBy(() -> service.forceRelease(command(ADMIN, request), reason))
                    .isInstanceOf(InvalidRequestDataException.class);
        }
        verifyNoInteractions(reviews, audit);
    }

    // ---- the idle job ---------------------------------------------------------------------------------------

    @Test
    void idleReviewsAreFreedAndAuditedAndTheOthersAreLeftAlone() {
        Request idle = request(RequestStatus.UNDER_REVIEW);
        Request fresh = request(RequestStatus.UNDER_REVIEW);
        Request busy = request(RequestStatus.UNDER_REVIEW);
        Request gone = request(RequestStatus.UNDER_REVIEW);
        when(reviews.idleReviews(NOW.minus(Duration.ofMinutes(60)))).thenReturn(
                List.of(idle.id(), fresh.id(), busy.id(), gone.id()));
        when(reviews.findForUpdate(idle.id())).thenReturn(Optional.of(new Review(idle,
                new ReviewLock(ALI.id(), "ali.b", NOW.minusSeconds(7200), NOW.minusSeconds(3600)), "0123")));
        when(reviews.findForUpdate(fresh.id())).thenReturn(Optional.of(new Review(fresh,
                new ReviewLock(ALI.id(), "ali.b", NOW.minusSeconds(7200), NOW.minusSeconds(60)), "0123")));
        when(reviews.findForUpdate(busy.id())).thenThrow(new RequestBusyException());
        when(reviews.findForUpdate(gone.id())).thenReturn(Optional.empty());

        int released = service.releaseIdle();

        assertThat(released).isEqualTo(1);
        verify(reviews).save(idle.id(), RequestStatus.PENDING_BRANCH_REVIEW, null, NOW);
        verify(reviews, never()).save(ArgumentMatchers.eq(fresh.id()), any(), any(), any());
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::userRole)
                .containsExactly("AUTO_RELEASE", "SYSTEM");
    }

    @Test
    void theServiceRunsEveryActionInOneTransaction() {
        assertThat(BranchReviewService.class.getAnnotation(Transactional.class)).isNotNull();
    }
}
