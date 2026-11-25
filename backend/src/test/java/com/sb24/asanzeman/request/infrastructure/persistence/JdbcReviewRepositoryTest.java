package com.sb24.asanzeman.request.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.ReviewLock;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2: the ownership lock, the branch rule's data, and what the branch records. */
class JdbcReviewRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcRequestRepository requests = new JdbcRequestRepository(jdbc);
    private final JdbcReviewRepository reviews = new JdbcReviewRepository(jdbc, requests);
    private final UUID agency = UUID.randomUUID();
    private final UUID holder = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.sql("INSERT INTO azs_business.agency (id, cif, code, name) VALUES (:id, '9001', '0042', 'Sky Travel')")
                .param("id", RawUuid.toBytes(agency), Types.VARBINARY).update();
        // 0123 was closed and is handled by 0456; 0456 is its own parent
        jdbc.sql("INSERT INTO azs_business.branch (branch_code, name, parent_branch, last_synced_at) "
                + "VALUES ('0456', 'Saadi', '0456', CURRENT_TIMESTAMP)").update();
        jdbc.sql("INSERT INTO azs_business.branch (branch_code, name, parent_branch, last_synced_at) "
                + "VALUES ('0123', 'Valiasr', '0456', CURRENT_TIMESTAMP)").update();
    }

    private UUID request(int counter, RequestStatus status) {
        var r = RequestRepositoryFixtures.request(agency, TrackingCode.of("0123", "0042", JalaliDate.of(1405, 9, 2),
                counter), List.of(), T0);
        var saved = new com.sb24.asanzeman.request.domain.model.Request(r.id(), r.trackingCode(), r.type(), status,
                agency, "0123", r.amount(), r.dueDate(), r.customer(), r.companions(), r.registeredBy(),
                r.registeredAt());
        requests.insert(saved);
        return saved.id();
    }

    private String column(UUID id, String column) {
        return jdbc.sql("SELECT " + column + " FROM azs_business.request WHERE id = :id")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).query(String.class).single();
    }

    @Test
    void aRequestWithoutAnOwnerIsReadWithNoLockAndTheBranchThatHandlesIt() {
        UUID id = request(1, RequestStatus.PENDING_BRANCH_REVIEW);

        var review = reviews.findForUpdate(id).orElseThrow();

        assertThat(review.request().id()).isEqualTo(id);
        assertThat(review.lock()).isNull();
        assertThat(review.request().branchCode()).isEqualTo("0123"); // the original branch is kept for history
        assertThat(review.handlingBranch()).isEqualTo("0456");
    }

    @Test
    void anUnknownRequestIsNotFound() {
        assertThat(reviews.findForUpdate(UUID.randomUUID())).isEmpty();
    }

    @Test
    void theOwnershipLockIsSavedWithTheStatusAndReadBackExactly() {
        UUID id = request(1, RequestStatus.PENDING_BRANCH_REVIEW);
        var lock = new ReviewLock(holder, "ali.b", T0, T0.plusSeconds(90));

        reviews.save(id, RequestStatus.UNDER_REVIEW, lock, T0.plusSeconds(100));

        var review = reviews.findForUpdate(id).orElseThrow();
        assertThat(review.request().status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(review.lock()).isEqualTo(lock);
    }

    @Test
    void clearingTheLockPutsTheRequestBackWithNoOwner() {
        UUID id = request(1, RequestStatus.PENDING_BRANCH_REVIEW);
        reviews.save(id, RequestStatus.UNDER_REVIEW, new ReviewLock(holder, "ali.b", T0, T0), T0);

        reviews.save(id, RequestStatus.PENDING_BRANCH_REVIEW, null, T0.plusSeconds(60));

        var review = reviews.findForUpdate(id).orElseThrow();
        assertThat(review.lock()).isNull();
        assertThat(review.request().status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        assertThat(column(id, "version")).isEqualTo("2");
    }

    private com.sb24.asanzeman.request.domain.model.Request revocationOf(UUID issuance, RequestStatus status, int counter) {
        var issued = requests.findById(issuance).orElseThrow();
        return issued.requestRevocation(UUID.randomUUID(), TrackingCode.of("0123", "0042", JalaliDate.of(1405, 9, 2),
                counter), status, "Sky Travel", T0);
    }

    @Test
    void aRevocationInTheQueueIsLinkedToItsIssuanceAndHasNoOwner() {
        UUID issuance = request(1, RequestStatus.ISSUED);
        var revocation = revocationOf(issuance, RequestStatus.PENDING_BRANCH_REVIEW, 2);

        reviews.insertRevocation(revocation, issuance, null);

        var review = reviews.findForUpdate(revocation.id()).orElseThrow();
        assertThat(review.request().type()).isEqualTo(com.sb24.asanzeman.request.domain.model.RequestType.REVOCATION);
        assertThat(review.parentId()).isEqualTo(issuance);
        assertThat(review.lock()).isNull();
        assertThat(review.revocationOpen()).isFalse(); // the flag is for an issuance request
        assertThat(reviews.findForUpdate(issuance).orElseThrow().revocationOpen()).isTrue();
        assertThat(reviews.findForUpdate(issuance).orElseThrow().parentId()).isNull();
    }

    @Test
    void aRevocationOfARequestSomeoneIsWorkingOnIsAlreadyHeldByThem() {
        UUID issuance = request(1, RequestStatus.PENDING_BRANCH_REVIEW);
        reviews.save(issuance, RequestStatus.UNDER_ACTION, new ReviewLock(holder, "ali.b", T0, T0), T0);
        var revocation = revocationOf(issuance, RequestStatus.UNDER_REVIEW, 2);
        var lock = new ReviewLock(holder, "ali.b", T0.plusSeconds(5), T0.plusSeconds(5));

        reviews.insertRevocation(revocation, issuance, lock);

        var review = reviews.findForUpdate(revocation.id()).orElseThrow();
        assertThat(review.request().status()).isEqualTo(RequestStatus.UNDER_REVIEW);
        assertThat(review.lock()).isEqualTo(lock);
        assertThat(reviews.findForUpdate(issuance).orElseThrow().lock().holderId()).isEqualTo(holder);
    }

    @Test
    void aRevocationIsOpenOnlyWhileItIsNotFinished() {
        UUID issuance = request(1, RequestStatus.ISSUED);
        var revocation = revocationOf(issuance, RequestStatus.PENDING_BRANCH_REVIEW, 2);
        reviews.insertRevocation(revocation, issuance, null);
        assertThat(reviews.findForUpdate(issuance).orElseThrow().revocationOpen()).isTrue();

        for (RequestStatus finished : List.of(RequestStatus.REJECTED, RequestStatus.COMPLETED)) {
            reviews.save(revocation.id(), finished, null, T0);
            assertThat(reviews.findForUpdate(issuance).orElseThrow().revocationOpen()).isFalse();
        }
        for (RequestStatus open : List.of(RequestStatus.UNDER_REVIEW, RequestStatus.UNDER_ACTION)) {
            reviews.save(revocation.id(), open, new ReviewLock(holder, "a", T0, T0), T0);
            assertThat(reviews.findForUpdate(issuance).orElseThrow().revocationOpen()).isTrue();
        }
    }

    @Test
    void theRejectionAndTheGuaranteeNumberAreRecorded() {
        UUID reject = request(1, RequestStatus.UNDER_REVIEW);
        UUID issue = request(2, RequestStatus.UNDER_ACTION);
        UUID reason = reason("ISSUANCE", 1);

        reviews.recordRejection(reject, reason, "documents are incomplete");
        reviews.recordIssue(issue, "GT-001", T0);

        assertThat(column(reject, "rejection_comment")).isEqualTo("documents are incomplete");
        assertThat(column(reject, "rejection_reason_id")).isNotNull();
        assertThat(column(issue, "guarantee_number")).isEqualTo("GT-001");
        assertThat(column(issue, "issued_at")).isNotNull();
    }

    @Test
    void aGuaranteeNumberAlreadyOnAnotherRequestIsRefused() {
        UUID first = request(1, RequestStatus.UNDER_ACTION);
        UUID second = request(2, RequestStatus.UNDER_ACTION);
        reviews.recordIssue(first, "GT-001", T0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reviews.recordIssue(second, "GT-001", T0))
                .isInstanceOf(com.sb24.asanzeman.request.domain.model.GuaranteeNumberTakenException.class);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM azs_business.request WHERE id = :id AND guarantee_number IS NULL")
                .param("id", RawUuid.toBytes(second), Types.VARBINARY).query(Integer.class).single()).isEqualTo(1);
        reviews.recordIssue(second, "GT-002", T0); // another number is fine
    }

    @Test
    void onlyAnActiveIssuanceReasonCanBeUsed() {
        UUID active = reason("ISSUANCE", 1);
        UUID inactive = reason("ISSUANCE", 0);
        UUID revocation = reason("REVOCATION", 1);

        assertThat(reviews.reasonExists(active, "ISSUANCE")).isTrue();
        assertThat(reviews.reasonExists(inactive, "ISSUANCE")).isFalse();
        assertThat(reviews.reasonExists(revocation, "ISSUANCE")).isFalse();
        assertThat(reviews.reasonExists(revocation, "REVOCATION")).isTrue();
        assertThat(reviews.reasonExists(active, "REVOCATION")).isFalse();
        assertThat(reviews.reasonExists(UUID.randomUUID(), "ISSUANCE")).isFalse();
    }

    @Test
    void theReasonsListHoldsOnlyTheActiveIssuanceReasonsInTheirOrder() {
        reason("ISSUANCE", 1, 2, "Second");
        reason("ISSUANCE", 1, 1, "First");
        reason("ISSUANCE", 0, 0, "Switched off");
        reason("REVOCATION", 1, 0, "Revocation only");

        assertThat(reviews.reasons("ISSUANCE")).extracting(com.sb24.asanzeman.request.domain.model.RejectionReason::text)
                .containsExactly("First", "Second");
    }

    @Test
    void theIdleReviewsAreThoseInReviewNobodyTouchedSinceTheLimit() {
        UUID idle = request(1, RequestStatus.PENDING_BRANCH_REVIEW);
        UUID fresh = request(2, RequestStatus.PENDING_BRANCH_REVIEW);
        UUID inAction = request(3, RequestStatus.PENDING_BRANCH_REVIEW);
        UUID queued = request(4, RequestStatus.PENDING_BRANCH_REVIEW);
        reviews.save(idle, RequestStatus.UNDER_REVIEW, new ReviewLock(holder, "a", T0, T0), T0);
        reviews.save(fresh, RequestStatus.UNDER_REVIEW, new ReviewLock(holder, "a", T0, T0.plusSeconds(3000)), T0);
        reviews.save(inAction, RequestStatus.UNDER_ACTION, new ReviewLock(holder, "a", T0, T0), T0);

        assertThat(reviews.idleReviews(T0.plusSeconds(2999))).containsExactly(idle);
        assertThat(reviews.idleReviews(T0.plusSeconds(3000))).containsExactly(idle, fresh); // inclusive, oldest first
        assertThat(reviews.idleReviews(T0.minusSeconds(1))).isEmpty();
        assertThat(queued).isNotNull();
    }

    private UUID reason(String kind, int active) {
        return reason(kind, active, 0, "t");
    }

    private UUID reason(String kind, int active, int order, String text) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO azs_business.rejection_reason (id, kind, text, active, sort_order) "
                + "VALUES (:id, :kind, :text, :active, :order)")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).param("kind", kind).param("text", text)
                .param("active", active).param("order", order).update();
        return id;
    }
}
