package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The branch's read side: the search criteria, a row and the details. */
class BranchModelTest {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");
    private static final BranchUser ON_0123 = new BranchUser(UUID.randomUUID(), "ali", "0123", false);
    private static final BranchUser ON_0456 = new BranchUser(UUID.randomUUID(), "reza", "0456", false);
    private static final BranchUser ON_0999 = new BranchUser(UUID.randomUUID(), "omid", "0999", false);

    private static RequestSummary summary() {
        return new RequestSummary(UUID.randomUUID(), "1", RequestType.ISSUANCE, RequestStatus.PENDING_BRANCH_REVIEW,
                "1000000011", "555", "Ali", 1000, "0123", "Valiasr", LocalDate.of(2027, 1, 20), T0, "Sky", "0912");
    }

    @Test
    void noStatusMeansTheInbox() {
        var criteria = new BranchSearchCriteria("0123", null, null, null, null, null, null);

        assertThat(criteria.statuses()).containsExactlyInAnyOrder(RequestStatus.PENDING_BRANCH_REVIEW,
                RequestStatus.UNDER_REVIEW, RequestStatus.UNDER_ACTION);
        assertThat(new BranchSearchCriteria("0123", Set.of(), null, null, null, null, null).statuses())
                .isEqualTo(BranchSearchCriteria.INBOX);
    }

    @Test
    void givenStatusesAreKeptAsTheyAre() {
        var criteria = new BranchSearchCriteria(null, Set.of(RequestStatus.ISSUED, RequestStatus.REJECTED), null, null,
                null, null, null);

        assertThat(criteria.statuses()).containsExactlyInAnyOrder(RequestStatus.ISSUED, RequestStatus.REJECTED);
    }

    @Test
    void aRequestThatHasNotReachedTheBranchCannotBeListedAndADateRangeCannotRunBackwards() {
        assertThatThrownBy(() -> new BranchSearchCriteria("0123", Set.of(RequestStatus.REGISTERED_AT_AGENCY), null,
                null, null, null, null)).isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> new BranchSearchCriteria("0123", null, null, null, null, T0, T0.minusSeconds(1)))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThat(new BranchSearchCriteria("0123", null, null, null, null, T0, T0).registeredTo()).isEqualTo(T0);
    }

    @Test
    void aRowIsActionableByTheOriginalBranchAndTheOneThatHandlesItNow() {
        var row = new BranchRequestRow(summary(), "ali", T0, "0456", null, null, null);

        assertThat(row.canAct(ON_0123)).isTrue();
        assertThat(row.canAct(ON_0456)).isTrue();
        assertThat(row.canAct(ON_0999)).isFalse();
    }

    @Test
    void theDetailsKeepTheirOwnCopyOfTheListsAndAReasonHasItsText() {
        var companions = new ArrayList<Companion>();
        var collateral = new ArrayList<CollateralAllocation>();
        var details = new BranchRequestDetails(new BranchRequestRow(summary(), null, null, "0123", null, null, null),
                companions, collateral);
        companions.add(null);
        collateral.add(null);

        assertThat(details.companions()).isEmpty();
        assertThat(details.collateral()).isEmpty();
        assertThat(new RejectionReason(UUID.randomUUID(), "text").text()).isEqualTo("text");
    }

}
