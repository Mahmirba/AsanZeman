package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunUseCase;
import com.sb24.asanzeman.request.domain.model.AgencyInfo;
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
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
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
    private CustomerNotifierPort notifier;
    @Mock
    private InquiryRunUseCase inquiries;
    @Mock
    private AgencyRegistryPort agencies;
    @Mock
    private BranchRepositoryPort branches;
    @Mock
    private RecordAuditUseCase audit;

    private BranchReviewService service;

    @BeforeEach
    void setUp() {
        service = new BranchReviewService(reviews, notifier, agencies, branches, audit, new MutableClock(NOW),
                new ReviewSettings(Duration.ofMinutes(60)), inquiries);
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
        when(agencies.find(request.agencyId())).thenReturn(
                Optional.of(new AgencyInfo(request.agencyId(), "0042", "Sky Travel", true)));
        when(branches.nameOf("0123")).thenReturn(Optional.of("Valiasr"));

        ReviewResult result = service.reject(command(ALI, request), REASON, "  documents are incomplete ");

        assertThat(result.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(result.heldBy()).isNull();
        verify(reviews).recordRejection(request.id(), REASON, "documents are incomplete");
        verify(notifier).requestRejected(request.id(), request.customer().mobile(), "Sky Travel", "Valiasr",
                "1405/09/02", "");
    }

    @Test
    void aRejectionNeedsACommentAndAKnownActiveReasonBeforeAnythingIsTouched() {
        Request request = request(RequestStatus.UNDER_REVIEW);

        for (String comment : new String[] {null, "", "   ", "x".repeat(201)}) {
            assertThatThrownBy(() -> service.reject(command(ALI, request), REASON, comment))
                    .isInstanceOf(InvalidRequestDataException.class);
        }
        verifyNoInteractions(reviews, notifier, audit); // a bad comment is refused before the request is even read

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
        verifyNoInteractions(notifier, audit);
    }

    @Test
    void aCommentOfExactlyTwoHundredCharactersIsAccepted() {
        Request request = request(RequestStatus.UNDER_REVIEW);
        found(request, ALI);
        when(reviews.reasonExists(REASON, "ISSUANCE")).thenReturn(true);
        when(branches.nameOf(anyString())).thenReturn(Optional.empty());

        service.reject(command(ALI, request), REASON, "x".repeat(200));

        verify(notifier).requestRejected(request.id(), request.customer().mobile(), "", "0123", "1405/09/02", "");
    }

    // ---- issue ----------------------------------------------------------------------------------------------

    @Test
    void issuingRecordsTheGuaranteeNumberAndTellsTheCustomer() {
        Request request = request(RequestStatus.UNDER_ACTION);
        found(request, ALI);
        when(agencies.find(request.agencyId())).thenReturn(
                Optional.of(new AgencyInfo(request.agencyId(), "0042", "Sky Travel", true)));
        when(branches.nameOf("0123")).thenReturn(Optional.of("Valiasr"));

        ReviewResult result = service.issue(command(ALI, request), " GT-001/2026 ");

        assertThat(result.status()).isEqualTo(RequestStatus.ISSUED);
        assertThat(result.heldBy()).isNull();
        verify(reviews).recordIssue(request.id(), "GT-001/2026", NOW);
        verify(notifier).guaranteeIssued(request.id(), request.customer().mobile(), "Sky Travel", "Valiasr",
                "1405/09/02", "");
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
        verifyNoInteractions(reviews, notifier, audit);
    }

    @Test
    void issuingBeforeTheApprovalIsRefusedAndNothingIsSent() {
        Request request = request(RequestStatus.UNDER_REVIEW);
        found(request, ALI);

        assertThatThrownBy(() -> service.issue(command(ALI, request), "GT-1"))
                .isInstanceOf(RequestNotReviewableException.class);
        verifyNoInteractions(notifier);
        verify(reviews, never()).recordIssue(any(), any(), any());
    }

    // ---- the salutation line (6.4) ------------------------------------------------------------------------

    @Test
    void theCustomerIsAddressedByTheRegistryGenderWhenItIsKnown() {
        Request request = request(RequestStatus.UNDER_ACTION);
        found(request, ALI);
        when(inquiries.genderOf(request.id())).thenReturn(Optional.of(Gender.MALE));
        when(branches.nameOf(anyString())).thenReturn(Optional.of("Valiasr"));

        service.issue(command(ALI, request), "GT-1");

        verify(notifier).guaranteeIssued(request.id(), request.customer().mobile(), "", "Valiasr", "1405/09/02",
                "آقا Ali Rezaei\n");
    }

    @Test
    void aRevocationIsAddressedByTheGenderOfItsIssuanceRequest() {
        UUID issuanceId = UUID.randomUUID();
        Request revocation = revocation(RequestStatus.UNDER_REVIEW);
        foundRevocation(revocation, issuanceId);
        when(reviews.reasonExists(REASON, "REVOCATION")).thenReturn(true);
        when(inquiries.genderOf(issuanceId)).thenReturn(Optional.of(Gender.FEMALE));
        when(branches.nameOf(anyString())).thenReturn(Optional.of("Valiasr"));

        service.reject(command(ALI, revocation), REASON, "still in use");

        verify(notifier).revocationRejected(revocation.id(), revocation.customer().mobile(), "", "Valiasr",
                "1405/09/02", "خانم Ali Rezaei\n");
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
        foundRevocation(revocation, UUID.randomUUID());
        when(reviews.reasonExists(REASON, "REVOCATION")).thenReturn(true);
        when(agencies.find(revocation.agencyId())).thenReturn(
                Optional.of(new AgencyInfo(revocation.agencyId(), "0042", "Sky Travel", true, "09129990000")));
        when(branches.nameOf("0123")).thenReturn(Optional.of("Valiasr"));

        ReviewResult result = service.reject(command(ALI, revocation), REASON, "still in use");

        assertThat(result.status()).isEqualTo(RequestStatus.REJECTED);
        verify(reviews).recordRejection(revocation.id(), REASON, "still in use");
        verify(notifier).revocationRejected(revocation.id(), revocation.customer().mobile(), "Sky Travel", "Valiasr",
                "1405/09/02", "");
        verify(notifier, never()).requestRejected(any(), any(), any(), any(), any(), any());
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
        verifyNoInteractions(notifier, audit);
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
        when(agencies.find(issued.agencyId())).thenReturn(
                Optional.of(new AgencyInfo(issued.agencyId(), "0042", "Sky Travel", true, "09129990000")));
        when(branches.nameOf("0123")).thenReturn(Optional.of("Valiasr"));

        ReviewResult result = service.completeRevocation(command(ALI, revocation));

        assertThat(result.status()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(result.heldBy()).isNull();
        verify(reviews).save(revocation.id(), RequestStatus.COMPLETED, null, NOW);
        verify(reviews).save(issuanceId, RequestStatus.REVOKED, null, NOW);
        verify(notifier).guaranteeRevoked(issuanceId, issued.customer().mobile(), "Sky Travel", "Valiasr",
                "1405/09/02", "");
        verify(notifier).agencyRevocationCompleted(revocation.id(), "09129990000",
                revocation.trackingCode().value(), "Valiasr", "1405/09/02");
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
        when(branches.nameOf(anyString())).thenReturn(Optional.empty());

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
