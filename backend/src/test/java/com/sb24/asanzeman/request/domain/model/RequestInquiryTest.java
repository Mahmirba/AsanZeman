package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.Rials;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** A request waits at the agency's step for the customer's inquiries, and a negative one rejects it (5.1). */
class RequestInquiryTest {

    private static Request request(RequestType type, RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, type, status, UUID.randomUUID(), "0123",
                Rials.of(1000), LocalDate.of(2027, 1, 20), RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"),
                List.of(), "Sky Travel", Instant.parse("2026-11-21T10:00:00Z"));
    }

    @Test
    void onlyAnIssuanceRequestStillAtTheAgencyStepCanBeInquired() {
        assertThat(request(RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY).canBeInquired()).isTrue();
        assertThat(request(RequestType.REVOCATION, RequestStatus.REGISTERED_AT_AGENCY).canBeInquired()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = RequestStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "REGISTERED_AT_AGENCY")
    void aRequestPastTheAgencyStepCannotBeInquired(RequestStatus status) {
        assertThat(request(RequestType.ISSUANCE, status).canBeInquired()).isFalse();
    }

    @Test
    void aNegativeInquiryRejectsTheRequestAndChangesNothingElse() {
        Request original = request(RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY);

        Request rejected = original.rejectedByInquiry();

        assertThat(rejected.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(rejected).usingRecursiveComparison().ignoringFields("status").isEqualTo(original);
        assertThat(original.status()).isEqualTo(RequestStatus.REGISTERED_AT_AGENCY);
    }

    @Test
    void aRequestNotCompletedInTimeIsRejectedOnlyWhileItStillWaitsForTheCustomer() {
        Request original = request(RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY);

        assertThat(original.expired().status()).isEqualTo(RequestStatus.REJECTED);
        assertThatThrownBy(() -> request(RequestType.ISSUANCE, RequestStatus.PENDING_BRANCH_REVIEW).expired())
                .isInstanceOf(RequestNotInquirableException.class);
        assertThatThrownBy(() -> request(RequestType.REVOCATION, RequestStatus.REGISTERED_AT_AGENCY).expired())
                .isInstanceOf(RequestNotInquirableException.class);
    }

    @Test
    void aRequestThatIsNoLongerWaitingCannotBeRejectedByAnInquiry() {
        assertThatThrownBy(() -> request(RequestType.ISSUANCE, RequestStatus.CANCELLED).rejectedByInquiry())
                .isInstanceOf(RequestNotInquirableException.class);
        assertThatThrownBy(() -> request(RequestType.REVOCATION, RequestStatus.REGISTERED_AT_AGENCY)
                .rejectedByInquiry()).isInstanceOf(RequestNotInquirableException.class);
    }

    @Test
    void submittingSendsAnIssuanceRequestToTheBranchAndChangesNothingElse() {
        Request original = request(RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY);

        Request submitted = original.submitted();

        assertThat(submitted.status()).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        assertThat(submitted).usingRecursiveComparison().ignoringFields("status").isEqualTo(original);
    }

    @Test
    void aRequestThatIsNotAtTheAgencyStepCannotBeSubmitted() {
        assertThatThrownBy(() -> request(RequestType.ISSUANCE, RequestStatus.REJECTED).submitted())
                .isInstanceOf(RequestNotSubmittableException.class).hasMessageContaining("REJECTED");
        assertThatThrownBy(() -> request(RequestType.REVOCATION, RequestStatus.REGISTERED_AT_AGENCY).submitted())
                .isInstanceOf(RequestNotSubmittableException.class);
        assertThat(new RequestNotSubmittableException("x").kind()).isEqualTo(DomainException.Kind.CONFLICT);
        assertThat(new RequestNotSubmittableException("x").reason()).isEqualTo("x");
    }

    @Test
    void theRefusalIsAConflict() {
        assertThat(new RequestNotInquirableException().kind()).isEqualTo(DomainException.Kind.CONFLICT);
    }
}
