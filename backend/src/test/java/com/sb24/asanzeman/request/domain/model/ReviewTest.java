package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.Rials;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The branch's work on a request (4.1, 4.4): the branch rule, the ownership lock, and each step's place. */
class ReviewTest {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(600);

    private static final BranchUser ALI = new BranchUser(UUID.randomUUID(), "ali.b", "0123", false);
    private static final BranchUser SARA = new BranchUser(UUID.randomUUID(), "sara.b", "0123", false);
    private static final BranchUser REPLACEMENT = new BranchUser(UUID.randomUUID(), "reza.r", "0456", false);
    private static final BranchUser STRANGER = new BranchUser(UUID.randomUUID(), "omid.o", "0999", false);

    private static Request request(RequestType type, RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, type, status, UUID.randomUUID(), "0123",
                Rials.of(1000), LocalDate.of(2027, 1, 20), RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"),
                List.of(), "Sky Travel", T0);
    }

    /** Branch 0123 was closed; 0456 handles it now. */
    private static Review pending() {
        return new Review(request(RequestType.ISSUANCE, RequestStatus.PENDING_BRANCH_REVIEW), null, "0456");
    }

    private static Review heldBy(BranchUser user, RequestStatus status) {
        return new Review(request(RequestType.ISSUANCE, status), new ReviewLock(user.id(), user.name(), T0, T0),
                "0456");
    }

    // ---- who may act (4.4) ----------------------------------------------------------------------------------

    @Test
    void theOriginalBranchAndTheBranchThatReplacedItMayActButNoOtherBranch() {
        assertThat(pending().canAct(ALI)).isTrue();
        assertThat(pending().canAct(REPLACEMENT)).isTrue();
        assertThat(pending().canAct(STRANGER)).isFalse();
    }

    @Test
    void aStrangerCannotTakeOrDoAnything() {
        assertThatThrownBy(() -> pending().take(STRANGER, T0)).isInstanceOf(NotYourBranchException.class);
        assertThatThrownBy(() -> heldBy(STRANGER, RequestStatus.UNDER_REVIEW).approve(STRANGER, T1))
                .isInstanceOf(NotYourBranchException.class); // even a lock he somehow holds does not help
        assertThat(new NotYourBranchException().kind()).isEqualTo(DomainException.Kind.FORBIDDEN);
    }

    // ---- «اقدام» ---------------------------------------------------------------------------------------------

    @Test
    void takingARequestInTheQueueStartsTheReviewAndOwnsIt() {
        Review taken = pending().take(ALI, T0);

        assertThat(taken.request().status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(taken.lock()).isEqualTo(new ReviewLock(ALI.id(), "ali.b", T0, T0));
        assertThat(taken.handlingBranch()).isEqualTo("0456");
    }

    @Test
    void theReplacementBranchCanTakeIt() {
        assertThat(pending().take(REPLACEMENT, T0).lock().holderName()).isEqualTo("reza.r");
    }

    @Test
    void theHolderTakingItAgainJustComesBackToItAndKeepsItsStatus() {
        Review review = heldBy(ALI, RequestStatus.UNDER_ACTION);

        Review again = review.take(ALI, T1);

        assertThat(again.request().status()).isEqualTo(RequestStatus.UNDER_ACTION);
        assertThat(again.lock().lockedAt()).isEqualTo(T0);
        assertThat(again.lock().lastActivityAt()).isEqualTo(T1);
    }

    @Test
    void anotherUserCannotTakeARequestSomeoneHolds() {
        assertThatThrownBy(() -> heldBy(ALI, RequestStatus.UNDER_REVIEW).take(SARA, T1))
                .isInstanceOf(RequestLockedByOtherException.class)
                .satisfies(e -> {
                    var ex = (RequestLockedByOtherException) e;
                    assertThat(ex.kind()).isEqualTo(DomainException.Kind.CONFLICT);
                    // the holder and when (Jalali date, Tehran time), for the message
                    assertThat(ex.args()).containsExactly("ali.b", "1405/09/02 13:30");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"REGISTERED_AT_AGENCY", "ISSUED", "REJECTED", "CANCELLED", "REVOKED", "COMPLETED",
            "UNDER_REVIEW", "UNDER_ACTION"})
    void aRequestThatIsNotInTheQueueAndHasNoHolderCannotBeTaken(RequestStatus status) {
        assertThatThrownBy(() -> new Review(request(RequestType.ISSUANCE, status), null, "0456").take(ALI, T0))
                .isInstanceOf(RequestNotReviewableException.class);
    }

    @Test
    void aRevocationRequestIsTakenThroughTheSameFlow() {
        var review = new Review(request(RequestType.REVOCATION, RequestStatus.PENDING_BRANCH_REVIEW), null, "0456");

        Review taken = review.take(ALI, T0);

        assertThat(taken.request().status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(taken.lock().holderId()).isEqualTo(ALI.id());
    }

    // ---- approve, reject, issue -----------------------------------------------------------------------------

    @Test
    void theHolderApprovesAndKeepsTheRequestThroughTheSigningAndFarangin() {
        Review approved = heldBy(ALI, RequestStatus.UNDER_REVIEW).approve(ALI, T1);

        assertThat(approved.request().status()).isEqualTo(RequestStatus.UNDER_ACTION);
        assertThat(approved.lock().holderId()).isEqualTo(ALI.id());
        assertThat(approved.lock().lastActivityAt()).isEqualTo(T1);
    }

    @Test
    void theHolderRejectsAndTheLockIsCleared() {
        Review rejected = heldBy(ALI, RequestStatus.UNDER_REVIEW).reject(ALI);

        assertThat(rejected.request().status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(rejected.lock()).isNull();
    }

    @Test
    void theHolderIssuesAndTheLockIsCleared() {
        Review issued = heldBy(ALI, RequestStatus.UNDER_ACTION).issue(ALI);

        assertThat(issued.request().status()).isEqualTo(RequestStatus.ISSUED);
        assertThat(issued.lock()).isNull();
    }

    @Test
    void eachStepIsAllowedFromItsOwnStatusOnly() {
        assertThatThrownBy(() -> heldBy(ALI, RequestStatus.UNDER_ACTION).approve(ALI, T1))
                .isInstanceOf(RequestNotReviewableException.class);
        assertThatThrownBy(() -> heldBy(ALI, RequestStatus.UNDER_ACTION).reject(ALI))
                .isInstanceOf(RequestNotReviewableException.class);
        assertThatThrownBy(() -> heldBy(ALI, RequestStatus.UNDER_REVIEW).issue(ALI))
                .isInstanceOf(RequestNotReviewableException.class)
                .hasMessageContaining("UNDER_REVIEW");
    }

    @Test
    void anyStepByAnotherUserIsRefusedWithTheHoldersName() {
        var review = heldBy(ALI, RequestStatus.UNDER_REVIEW);

        assertThatThrownBy(() -> review.approve(SARA, T1)).isInstanceOf(RequestLockedByOtherException.class);
        assertThatThrownBy(() -> review.reject(SARA)).isInstanceOf(RequestLockedByOtherException.class);
        assertThatThrownBy(() -> review.abandon(SARA)).isInstanceOf(RequestLockedByOtherException.class);
        assertThatThrownBy(() -> heldBy(ALI, RequestStatus.UNDER_ACTION).issue(SARA))
                .isInstanceOf(RequestLockedByOtherException.class);
    }

    @Test
    void aStepOnARequestNobodyTookIsRefused() {
        assertThatThrownBy(() -> pending().approve(ALI, T1)).isInstanceOf(RequestNotReviewableException.class)
                .hasMessageContaining("اقدام");
        assertThatThrownBy(() -> pending().reject(ALI)).isInstanceOf(RequestNotReviewableException.class);
        assertThatThrownBy(() -> pending().issue(ALI)).isInstanceOf(RequestNotReviewableException.class);
        assertThatThrownBy(() -> pending().abandon(ALI)).isInstanceOf(RequestNotReviewableException.class);
        assertThat(new RequestNotReviewableException("x").kind()).isEqualTo(DomainException.Kind.CONFLICT);
        assertThat(new RequestNotReviewableException("x").reason()).isEqualTo("x");
    }

    // ---- abandon, force release, auto release ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"UNDER_REVIEW", "UNDER_ACTION"})
    void theHolderMayAbandonInReviewAndCancelTheActionAndTheRequestGoesBackToTheQueue(RequestStatus status) {
        Review released = heldBy(ALI, status).abandon(ALI);

        assertThat(released.request().status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        assertThat(released.lock()).isNull();
    }

    @Test
    void anAdminCanReleaseARequestWhoeverHoldsItInEitherState() {
        assertThat(heldBy(ALI, RequestStatus.UNDER_REVIEW).forceRelease().request().status())
                .isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        var released = heldBy(ALI, RequestStatus.UNDER_ACTION).forceRelease();

        assertThat(released.request().status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        assertThat(released.lock()).isNull();
    }

    @Test
    void thereIsNothingToForceReleaseOnARequestNobodyHolds() {
        assertThatThrownBy(() -> pending().forceRelease()).isInstanceOf(RequestNotReviewableException.class);
    }

    @Test
    void aReviewLeftAloneForTheIdleTimeGoesBackToTheQueue() {
        var review = heldBy(ALI, RequestStatus.UNDER_REVIEW);

        assertThat(review.autoReleased(T0.plus(Duration.ofMinutes(60)), Duration.ofMinutes(60))).hasValueSatisfying(r -> {
            assertThat(r.request().status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
            assertThat(r.lock()).isNull();
        });
        assertThat(review.autoReleased(T0.plus(Duration.ofMinutes(59)), Duration.ofMinutes(60))).isEmpty();
    }

    @Test
    void aRequestInActionNeverGoesBackOnItsOwnAndNeitherDoesOneNobodyHolds() {
        assertThat(heldBy(ALI, RequestStatus.UNDER_ACTION).autoReleased(T0.plus(Duration.ofDays(30)),
                Duration.ofMinutes(60))).isEmpty();
        assertThat(pending().autoReleased(T0.plus(Duration.ofDays(30)), Duration.ofMinutes(60))).isEmpty();
    }

    @Test
    void releasingARequestThatNobodyIsWorkingOnIsRefused() {
        assertThatThrownBy(() -> request(RequestType.ISSUANCE, RequestStatus.ISSUED).releasedToBranch())
                .isInstanceOf(RequestNotReviewableException.class);
        assertThatThrownBy(() -> request(RequestType.REVOCATION, RequestStatus.PENDING_BRANCH_REVIEW).releasedToBranch())
                .isInstanceOf(RequestNotReviewableException.class);
        assertThat(request(RequestType.REVOCATION, RequestStatus.UNDER_REVIEW).releasedToBranch().status())
                .isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
    }

    // ---- the small types ------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"1", "ABC-123", "12/34/56", "gt_001", "123456789012345678901234567890"})
    void aGuaranteeNumberOfLettersDigitsAndSeparatorsIsAccepted(String value) {
        assertThat(new GuaranteeNumber(value).value()).isEqualTo(value);
    }

    @Test
    void aGuaranteeNumberIsTrimmedAndAnythingElseIsRefused() {
        assertThat(new GuaranteeNumber("  12345 ").value()).isEqualTo("12345");
        for (String bad : new String[] {null, "", "  ", "12 34", "1234567890123456789012345678901", "سلام", "12;34"}) {
            assertThatThrownBy(() -> new GuaranteeNumber(bad)).isInstanceOf(InvalidRequestDataException.class);
        }
    }

    @Test
    void aBranchUserNeedsAnIdANameAndABranch() {
        for (Object[] bad : new Object[][] {{null, "n", "0123"}, {UUID.randomUUID(), null, "0123"},
                {UUID.randomUUID(), " ", "0123"}, {UUID.randomUUID(), "n", null}, {UUID.randomUUID(), "n", " "}}) {
            assertThatThrownBy(() -> new BranchUser((UUID) bad[0], (String) bad[1], (String) bad[2], false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void aLockKnowsItsHolderAndCanBeTouched() {
        var lock = new ReviewLock(ALI.id(), "ali.b", T0, T0);

        assertThat(lock.heldBy(ALI)).isTrue();
        assertThat(lock.heldBy(SARA)).isFalse();
        assertThat(lock.touched(T1)).isEqualTo(new ReviewLock(ALI.id(), "ali.b", T0, T1));
    }
}
