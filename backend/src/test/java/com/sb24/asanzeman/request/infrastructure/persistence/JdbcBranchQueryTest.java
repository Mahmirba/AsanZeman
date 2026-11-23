package com.sb24.asanzeman.request.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.BranchSearchCriteria;
import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.model.ReviewLock;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2: who sees which request (4.4) and what the row shows (4.1). */
class JdbcBranchQueryTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcRequestRepository requests = new JdbcRequestRepository(jdbc);
    private final JdbcReviewRepository reviews = new JdbcReviewRepository(jdbc, requests);
    private final JdbcBranchQuery query = new JdbcBranchQuery(jdbc);
    private final UUID agency = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.sql("INSERT INTO azs_business.agency (id, cif, code, name) VALUES (:id, '9001', '0042', 'Sky Travel')")
                .param("id", RawUuid.toBytes(agency), Types.VARBINARY).update();
        // 0456 replaced the closed 0123; 0999 is a stranger; 0456 and 0999 are their own parents
        for (String[] b : new String[][] {{"0456", "Saadi", "0456"}, {"0123", "Valiasr", "0456"},
                {"0999", "Other", "0999"}}) {
            jdbc.sql("INSERT INTO azs_business.branch (branch_code, name, parent_branch, last_synced_at) "
                    + "VALUES (:c, :n, :p, CURRENT_TIMESTAMP)").param("c", b[0]).param("n", b[1]).param("p", b[2])
                    .update();
        }
    }

    private UUID request(int counter, String branch, String customerCode, RequestStatus status, Instant at) {
        var base = RequestRepositoryFixtures.request(agency, TrackingCode.of(branch, "0042",
                JalaliDate.of(1405, 8, 30), counter), List.of(), at);
        var c = base.customer();
        var saved = new Request(base.id(), base.trackingCode(), base.type(), status, agency, branch, base.amount(),
                base.dueDate(), new com.sb24.asanzeman.request.domain.model.CustomerSnapshot(c.cif(), customerCode,
                        c.firstName(), c.lastName(), c.fullName(), c.fatherName(), c.birthDate(), c.mobile(), null,
                        null, null, "ACTUAL", null), List.of(new Companion("Sara", "0012345679",
                                LocalDate.of(2016, 4, 1), "P1")), "Sky Travel", at);
        requests.insert(saved);
        return saved.id();
    }

    private BranchSearchCriteria branch(String code) {
        return new BranchSearchCriteria(code, null, null, null, null, null, null);
    }

    private List<UUID> ids(BranchSearchCriteria criteria) {
        return query.search(criteria, 0, 50).items().stream().map(r -> r.summary().id()).toList();
    }

    @Test
    void aBranchSeesItsOwnRequestsAndThoseOfTheBranchesItReplacedButNoOthers() {
        UUID closed = request(1, "0123", "1000000011", RequestStatus.PENDING_BRANCH_REVIEW, T0);
        UUID own = request(2, "0456", "1000000028", RequestStatus.PENDING_BRANCH_REVIEW, T0.plusSeconds(10));
        UUID other = request(3, "0999", "1000000036", RequestStatus.PENDING_BRANCH_REVIEW, T0.plusSeconds(20));

        assertThat(ids(branch("0456"))).containsExactly(own, closed); // newest first
        assertThat(ids(branch("0123"))).containsExactly(closed); // the original branch, even though it is closed
        assertThat(ids(branch("0999"))).containsExactly(other);
        assertThat(ids(branch(null))).containsExactly(other, own, closed); // an admin sees every branch
        assertThat(ids(branch("0777"))).isEmpty();
    }

    @Test
    void theInboxHoldsOnlyTheRequestsInWorkAndAStatusFilterChoosesOthers() {
        UUID waiting = request(1, "0123", "1000000011", RequestStatus.PENDING_BRANCH_REVIEW, T0);
        UUID review = request(2, "0123", "1000000011", RequestStatus.UNDER_REVIEW, T0.plusSeconds(1));
        UUID action = request(3, "0123", "1000000011", RequestStatus.UNDER_ACTION, T0.plusSeconds(2));
        UUID issued = request(4, "0123", "1000000011", RequestStatus.ISSUED, T0.plusSeconds(3));
        UUID rejected = request(5, "0123", "1000000011", RequestStatus.REJECTED, T0.plusSeconds(4));
        request(6, "0123", "1000000011", RequestStatus.REGISTERED_AT_AGENCY, T0.plusSeconds(5)); // not at the branch

        assertThat(ids(branch("0123"))).containsExactly(action, review, waiting);
        assertThat(ids(new BranchSearchCriteria("0123", Set.of(RequestStatus.ISSUED, RequestStatus.REJECTED), null,
                null, null, null, null))).containsExactly(rejected, issued);
    }

    @Test
    void filtersByNationalCodeCustomerNumberTrackingCodeAndTheRegistrationWindow() {
        UUID a = request(1, "0123", "1000000011", RequestStatus.PENDING_BRANCH_REVIEW, T0);
        UUID b = request(2, "0123", "1000000028", RequestStatus.PENDING_BRANCH_REVIEW, T0.plusSeconds(100));
        var tracking = query.find(a).orElseThrow().summary().trackingCode();

        assertThat(ids(new BranchSearchCriteria("0123", null, "1000000028", null, null, null, null))).containsExactly(b);
        assertThat(ids(new BranchSearchCriteria("0123", null, null, "555", null, null, null))).hasSize(2);
        assertThat(ids(new BranchSearchCriteria("0123", null, null, null, tracking, null, null))).containsExactly(a);
        assertThat(ids(new BranchSearchCriteria("0123", null, null, null, null, T0.plusSeconds(1), null)))
                .containsExactly(b);
        assertThat(ids(new BranchSearchCriteria("0123", null, null, null, null, null, T0.plusSeconds(100))))
                .containsExactly(a); // the end of the window is not included
        assertThat(ids(new BranchSearchCriteria("0123", null, " ", " ", " ", null, null))).hasSize(2);
    }

    @Test
    void pagesAreCutByOffsetAndTheTotalCountsEverything() {
        for (int i = 1; i <= 5; i++) {
            request(i, "0123", "1000000011", RequestStatus.PENDING_BRANCH_REVIEW, T0.plusSeconds(i));
        }

        var page = query.search(branch("0123"), 1, 2);

        assertThat(page.total()).isEqualTo(5);
        assertThat(page.items()).hasSize(2);
        assertThat(query.search(branch("0123"), 5, 2).items()).isEmpty();
        assertThat(query.search(branch("0777"), 0, 2).total()).isZero();
    }

    @Test
    void aHeldRequestShowsWhoHoldsItAndTheBranchThatHandlesItAndTheRecordedRejectionAndNumber() {
        UUID held = request(1, "0123", "1000000011", RequestStatus.PENDING_BRANCH_REVIEW, T0);
        UUID rejected = request(2, "0123", "1000000011", RequestStatus.UNDER_REVIEW, T0.plusSeconds(1));
        UUID issued = request(3, "0123", "1000000011", RequestStatus.UNDER_ACTION, T0.plusSeconds(2));
        reviews.save(held, RequestStatus.UNDER_REVIEW, new ReviewLock(UUID.randomUUID(), "ali.b", T0, T0), T0);
        UUID reason = UUID.randomUUID();
        jdbc.sql("INSERT INTO azs_business.rejection_reason (id, kind, text) VALUES (:id, 'ISSUANCE', 'Incomplete')")
                .param("id", RawUuid.toBytes(reason), Types.VARBINARY).update();
        reviews.save(rejected, RequestStatus.REJECTED, null, T0);
        reviews.recordRejection(rejected, reason, "missing page");
        reviews.save(issued, RequestStatus.ISSUED, null, T0);
        reviews.recordIssue(issued, "GT-1", T0);

        BranchRequestRow heldRow = query.find(held).orElseThrow();
        BranchRequestRow rejectedRow = query.find(rejected).orElseThrow();
        BranchRequestRow issuedRow = query.find(issued).orElseThrow();

        assertThat(heldRow.lockedBy()).isEqualTo("ali.b");
        assertThat(heldRow.lockedAt()).isEqualTo(T0);
        assertThat(heldRow.handlingBranch()).isEqualTo("0456");
        assertThat(heldRow.summary().branchName()).isEqualTo("Valiasr");
        assertThat(rejectedRow.lockedBy()).isNull();
        assertThat(rejectedRow.lockedAt()).isNull();
        assertThat(rejectedRow.rejectionReason()).isEqualTo("Incomplete");
        assertThat(rejectedRow.rejectionComment()).isEqualTo("missing page");
        assertThat(issuedRow.guaranteeNumber()).isEqualTo("GT-1");
    }

    @Test
    void aRequestThatHasNotReachedTheBranchIsNotFoundAndCompanionsComeInOrder() {
        UUID early = request(1, "0123", "1000000011", RequestStatus.REGISTERED_AT_AGENCY, T0);
        UUID sent = request(2, "0123", "1000000011", RequestStatus.PENDING_BRANCH_REVIEW, T0);

        assertThat(query.find(early)).isEmpty();
        assertThat(query.find(UUID.randomUUID())).isEmpty();
        assertThat(query.companions(sent)).extracting(Companion::fullName).containsExactly("Sara");
        assertThat(query.find(sent).map(BranchRequestRow::summary).map(RequestSummary::status))
                .contains(RequestStatus.PENDING_BRANCH_REVIEW);
    }
}
