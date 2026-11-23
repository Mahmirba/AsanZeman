package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRun;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunView;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunUseCase;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestNotInquirableException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestTest;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.shared.model.Rials;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerInquiryServiceTest {

    private static final String CUSTOMER = "1000000011";

    @Mock
    private RequestRepositoryPort requests;
    @Mock
    private InquiryRunUseCase inquiries;
    @InjectMocks
    private CustomerInquiryService service;

    private static Request request(RequestType type, RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, type, status, UUID.randomUUID(), "0123",
                Rials.of(250_000_000), LocalDate.of(2027, 1, 20),
                RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"), List.of(), "Sky Travel",
                Instant.parse("2026-11-21T10:00:00Z"));
    }

    private static InquiryRunView view() {
        return new InquiryRunView(InquiryRun.started(UUID.randomUUID(), UUID.randomUUID(), Instant.now()), List.of());
    }

    @Test
    void startHandsTheSnapshotOfTheRequestToTheInquiries() {
        Request request = request(RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY);
        var started = view();
        when(requests.findById(request.id())).thenReturn(Optional.of(request));
        when(inquiries.start(any(), any())).thenReturn(started);

        var result = service.start(request.customer().code(), request.id());

        assertThat(result).isSameAs(started);
        verify(inquiries).start(request.id(), new InquiryContext(request.customer().code(),
                request.customer().mobile(), 250_000_000L, "0123", LocalDate.of(1990, 5, 17)));
    }

    @Test
    void aRequestOfAnotherCustomerOrNoneIsNotFound() {
        Request request = request(RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.findById(request.id())).thenReturn(Optional.of(request));
        UUID unknown = UUID.randomUUID();
        when(requests.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start("1000000028", request.id()))
                .isInstanceOf(RequestNotFoundException.class);
        assertThatThrownBy(() -> service.start(CUSTOMER, unknown)).isInstanceOf(RequestNotFoundException.class);
        assertThatThrownBy(() -> service.get("1000000028", request.id(), UUID.randomUUID()))
                .isInstanceOf(RequestNotFoundException.class);
        verifyNoInteractions(inquiries);
    }

    @Test
    void onlyARequestStillAtTheAgencyStepIsInquired() {
        for (RequestStatus status : List.of(RequestStatus.CANCELLED, RequestStatus.REJECTED,
                RequestStatus.PENDING_BRANCH_REVIEW)) {
            Request request = request(RequestType.ISSUANCE, status);
            when(requests.findById(request.id())).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.start(request.customer().code(), request.id()))
                    .isInstanceOf(RequestNotInquirableException.class);
        }
        verify(inquiries, never()).start(any(), any());
    }

    @Test
    void readingARunChecksTheOwnerAndThenAsksTheInquiries() {
        Request request = request(RequestType.ISSUANCE, RequestStatus.REJECTED); // a finished request can be read
        UUID runId = UUID.randomUUID();
        var found = view();
        when(requests.findById(request.id())).thenReturn(Optional.of(request));
        when(inquiries.get(request.id(), runId)).thenReturn(found);

        assertThat(service.get(request.customer().code(), request.id(), runId)).isSameAs(found);
    }
}
