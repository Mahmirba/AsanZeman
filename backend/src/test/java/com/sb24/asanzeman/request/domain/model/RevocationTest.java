package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.model.Rials;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Revocation (4.2, D11, D17): the request it creates, its review, and what completing it does to the issuance. */
class RevocationTest {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(600);
    private static final TrackingCode CODE = TrackingCode.of("0123", "0042", JalaliDate.of(1405, 9, 2), 9);
    private static final BranchUser ALI = new BranchUser(UUID.randomUUID(), "ali.b", "0123", false);
    private static final BranchUser SARA = new BranchUser(UUID.randomUUID(), "sara.b", "0123", false);

    private static Request issuance(RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, RequestType.ISSUANCE, status, UUID.randomUUID(), "0123",
                Rials.of(1000), LocalDate.of(2027, 1, 20), RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"),
                List.of(new Companion("Sara", "0012345679", LocalDate.of(2016, 4, 1), "P1")), "Sky Travel", T0);
    }

    private static Request revocation(RequestStatus status) {
        return issuance(RequestStatus.ISSUED).requestRevocation(UUID.randomUUID(), CODE, status, "Sky Travel", T1);
    }

    private static ReviewLock lockOf(BranchUser user) {
        return new ReviewLock(user.id(), user.name(), T0, T0);
    }

    // ---- asking for it --------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"UNDER_REVIEW", "UNDER_ACTION", "ISSUED"})
    void fromTheBranchStartedOnTheAgencysActionIsARevocation(RequestStatus status) {
        assertThat(issuance(status).needsRevocation()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"REGISTERED_AT_AGENCY", "PENDING_BRANCH_REVIEW", "REJECTED",
            "CANCELLED", "REVOKED", "COMPLETED"})
    void beforeItAndAfterTheEndThereIsNothingToRevoke(RequestStatus status) {
        assertThat(issuance(status).needsRevocation()).isFalse();
        assertThatThrownBy(() -> issuance(status).requestRevocation(UUID.randomUUID(), CODE,
                RequestStatus.PENDING_BRANCH_REVIEW, "Sky", T1)).isInstanceOf(RequestNotCancellableException.class);
    }

    @Test
    void aRevocationRequestCannotItselfBeRevoked() {
        assertThat(revocation(RequestStatus.PENDING_BRANCH_REVIEW).needsRevocation()).isFalse();
    }

    @Test
    void theRevocationIsTheSameGuaranteeUnderANewCodeAndTheAgencysName() {
        Request issued = issuance(RequestStatus.ISSUED);

        Request revocation = issued.requestRevocation(UUID.randomUUID(), CODE, RequestStatus.PENDING_BRANCH_REVIEW,
                "Sky User", T1);

        assertThat(revocation.type()).isEqualTo(RequestType.REVOCATION);
        assertThat(revocation.status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        assertThat(revocation.trackingCode()).isEqualTo(CODE);
        assertThat(revocation.id()).isNotEqualTo(issued.id());
        assertThat(revocation.registeredBy()).isEqualTo("Sky User");
        assertThat(revocation.registeredAt()).isEqualTo(T1);
        assertThat(revocation).usingRecursiveComparison().comparingOnlyFields("agencyId", "branchCode", "amount",
                "dueDate", "customer", "companions").isEqualTo(issued);
    }

    // ---- reviewing it ---------------------------------------------------------------------------------------

    @Test
    void theBranchTakesApprovesAndCompletesARevocationAndTheLockIsClearedAtTheEnd() {
        var queued = new Review(revocation(RequestStatus.PENDING_BRANCH_REVIEW), null, "0123");

        Review taken = queued.take(ALI, T0);
        Review approved = taken.approve(ALI, T1);
        Review completed = approved.completeRevocation(ALI);

        assertThat(taken.request().status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(approved.request().status()).isEqualTo(RequestStatus.UNDER_ACTION);
        assertThat(completed.request().status()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(completed.lock()).isNull();
    }

    @Test
    void aRevocationCanBeRejectedAndAbandonedLikeAnyOtherRequest() {
        var held = new Review(revocation(RequestStatus.UNDER_REVIEW), lockOf(ALI), "0123");

        assertThat(held.reject(ALI).request().status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(held.abandon(ALI).request().status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
    }

    @Test
    void aGuaranteeNumberIsForAnIssuanceAndAConfirmationForARevocationOnly() {
        var revocationInAction = new Review(revocation(RequestStatus.UNDER_ACTION), lockOf(ALI), "0123");
        var issuanceInAction = new Review(issuance(RequestStatus.UNDER_ACTION), lockOf(ALI), "0123");

        assertThatThrownBy(() -> revocationInAction.issue(ALI)).isInstanceOf(RequestNotReviewableException.class)
                .hasMessageContaining("ISSUANCE");
        assertThatThrownBy(() -> issuanceInAction.completeRevocation(ALI))
                .isInstanceOf(RequestNotReviewableException.class).hasMessageContaining("REVOCATION");
    }

    @Test
    void completingARevocationNeedsItsHolderAndTheRightStatus() {
        var inReview = new Review(revocation(RequestStatus.UNDER_REVIEW), lockOf(ALI), "0123");
        var inAction = new Review(revocation(RequestStatus.UNDER_ACTION), lockOf(ALI), "0123");

        assertThatThrownBy(() -> inReview.completeRevocation(ALI)).isInstanceOf(RequestNotReviewableException.class);
        assertThatThrownBy(() -> inAction.completeRevocation(SARA))
                .isInstanceOf(RequestLockedByOtherException.class);
    }

    // ---- what it does to the issuance request ---------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"ISSUED", "UNDER_REVIEW", "UNDER_ACTION"})
    void aCompletedRevocationRevokesTheIssuanceWhateverStateItWasInAndFreesIt(RequestStatus status) {
        var issuance = new Review(issuance(status), status == RequestStatus.ISSUED ? null : lockOf(ALI), "0123");

        Review revoked = issuance.revoked();

        assertThat(revoked.request().status()).isEqualTo(RequestStatus.REVOKED);
        assertThat(revoked.lock()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"PENDING_BRANCH_REVIEW", "REJECTED", "CANCELLED", "REVOKED"})
    void anIssuanceThatWasNeverIssuedOrWorkedOnCannotBeRevoked(RequestStatus status) {
        assertThatThrownBy(() -> new Review(issuance(status), null, "0123").revoked())
                .isInstanceOf(RequestNotReviewableException.class);
    }

    @Test
    void aRevocationRequestIsNeverRevoked() {
        assertThatThrownBy(() -> new Review(revocation(RequestStatus.UNDER_ACTION), lockOf(ALI), "0123").revoked())
                .isInstanceOf(RequestNotReviewableException.class);
    }

    // ---- an issuance with a revocation open (D17) -----------------------------------------------------------

    private static Review withOpenRevocation(RequestStatus status) {
        return new Review(issuance(status), lockOf(ALI), "0123", null, true);
    }

    @Test
    void anIssuanceWithARevocationOpenCanNoLongerBeApprovedOrRejected() {
        assertThatThrownBy(() -> withOpenRevocation(RequestStatus.UNDER_REVIEW).approve(ALI, T1))
                .isInstanceOf(RequestNotReviewableException.class).hasMessageContaining("revocation");
        assertThatThrownBy(() -> withOpenRevocation(RequestStatus.UNDER_REVIEW).reject(ALI))
                .isInstanceOf(RequestNotReviewableException.class);
    }

    @Test
    void theHolderMayStillRecordAGuaranteeThatWasAlreadyIssuedAndLetGoOfTheRequest() {
        assertThat(withOpenRevocation(RequestStatus.UNDER_ACTION).issue(ALI).request().status())
                .isEqualTo(RequestStatus.ISSUED);
        assertThat(withOpenRevocation(RequestStatus.UNDER_ACTION).abandon(ALI).request().status())
                .isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
    }

    @Test
    void theParentAndTheOpenFlagSurviveEveryStep() {
        UUID parent = UUID.randomUUID();
        var review = new Review(revocation(RequestStatus.PENDING_BRANCH_REVIEW), null, "0123", parent, false);

        assertThat(review.take(ALI, T0).parentId()).isEqualTo(parent);
        assertThat(new Review(issuance(RequestStatus.UNDER_ACTION), lockOf(ALI), "0123", null, true)
                .issue(ALI).revocationOpen()).isTrue();
    }

    @Test
    void theDuplicateRefusalIsAConflict() {
        assertThat(new RevocationAlreadyRequestedException().kind()).isEqualTo(DomainException.Kind.CONFLICT);
    }
}
