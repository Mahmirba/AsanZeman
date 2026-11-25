package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.Rials;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The agency's cancel and revoke action (4.2) and the other small value types of the agency panel. */
class RequestCancelTest {

    private static Request request(RequestType type, RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, type, status, UUID.randomUUID(), "0123",
                Rials.of(1000), LocalDate.of(2027, 1, 20), RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"),
                List.of(), "Sky Travel", Instant.parse("2026-11-21T10:00:00Z"));
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"REGISTERED_AT_AGENCY", "PENDING_BRANCH_REVIEW"})
    void beforeTheBranchStartsARequestIsCancelledAtOnce(RequestStatus status) {
        Request original = request(RequestType.ISSUANCE, status);

        Request cancelled = original.cancelByAgency();

        assertThat(cancelled.status()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(cancelled).usingRecursiveComparison().ignoringFields("status").isEqualTo(original);
        assertThat(original.status()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"UNDER_REVIEW", "UNDER_ACTION", "ISSUED"})
    void afterTheBranchStartedOnlyARevocationCanUndoIt(RequestStatus status) {
        assertThatThrownBy(() -> request(RequestType.ISSUANCE, status).cancelByAgency())
                .isInstanceOf(RevocationNotAvailableException.class);
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, names = {"REJECTED", "CANCELLED", "REVOKED", "COMPLETED"})
    void aFinishedRequestHasNothingToCancel(RequestStatus status) {
        assertThatThrownBy(() -> request(RequestType.ISSUANCE, status).cancelByAgency())
                .isInstanceOf(RequestNotCancellableException.class);
    }

    @Test
    void aRevocationRequestIsNeverCancelledByTheAgency() {
        assertThatThrownBy(() -> request(RequestType.REVOCATION, RequestStatus.PENDING_BRANCH_REVIEW).cancelByAgency())
                .isInstanceOf(RequestNotCancellableException.class);
    }

    @Test
    void theAgencyPanelRefusalsHaveTheRightStatusKinds() {
        assertThat(new RequestNotFoundException().kind()).isEqualTo(DomainException.Kind.NOT_FOUND);
        assertThat(new RequestBusyException().kind()).isEqualTo(DomainException.Kind.CONFLICT);
        assertThat(new RequestNotCancellableException().kind()).isEqualTo(DomainException.Kind.CONFLICT);
        assertThat(new RevocationNotAvailableException().kind()).isEqualTo(DomainException.Kind.CONFLICT);
        assertThat(CancelOutcome.valueOf("REVOCATION_REQUESTED")).isNotNull();
    }

    @Test
    void aSearchIsAlwaysForOneAgencyAndTheDateRangeMustNotRunBackwards() {
        UUID agency = UUID.randomUUID();
        Instant from = Instant.parse("2026-11-01T00:00:00Z");

        assertThatThrownBy(() -> new RequestSearchCriteria(null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestSearchCriteria(agency, null, null, null, null, null, from,
                from.minusSeconds(1))).isInstanceOf(InvalidRequestDataException.class);
        assertThat(new RequestSearchCriteria(agency, null, null, null, null, null, from, from).types()).isEmpty();
        assertThat(new RequestSearchCriteria(agency, null, null, Set.of(RequestType.ISSUANCE), null, null, null, null)
                .types()).containsExactly(RequestType.ISSUANCE);
    }

    @Test
    void pagesAndDetailsCopyTheirLists() {
        var items = new ArrayList<>(List.of("a"));
        var page = new PageResult<>(items, 1, 0, 20);
        items.clear();

        assertThat(page.items()).containsExactly("a");
        var companions = new ArrayList<>(List.of(RequestTest.companion()));
        var summary = new RequestSummary(UUID.randomUUID(), "1", RequestType.ISSUANCE,
                RequestStatus.REGISTERED_AT_AGENCY, "1000000011", null, "Ali", 1, "0123", "Main",
                LocalDate.of(2027, 1, 20), Instant.now(), "Sky", null);
        var details = new RequestDetails(summary, companions);
        companions.clear();

        assertThat(details.companions()).hasSize(1);
        assertThat(new BranchSummary("0123", "Main", "Tehran").name()).isEqualTo("Main");
        assertThat(ZoneOffset.UTC).isNotNull();
    }
}
