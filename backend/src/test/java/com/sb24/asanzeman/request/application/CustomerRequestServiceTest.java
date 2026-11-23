package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.RequestDetails;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.port.out.RequestQueryPort;
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
class CustomerRequestServiceTest {

    private static final String CUSTOMER = "1000000011";

    @Mock
    private RequestQueryPort queries;
    @InjectMocks
    private CustomerRequestService service;

    private static RequestSummary summary(String mobile) {
        return new RequestSummary(UUID.randomUUID(), "1", RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY,
                CUSTOMER, "555", "Ali", 1, "0123", "Main", LocalDate.of(2027, 1, 20), Instant.now(), "Sky", mobile);
    }

    @Test
    void listsWhatTheQueryFindsForTheCustomer() {
        List<RequestSummary> found = List.of(summary("09121234567"));
        when(queries.forCustomer(CUSTOMER)).thenReturn(found);

        assertThat(service.list(CUSTOMER)).isEqualTo(found);
    }

    @Test
    void detailsOfTheCustomersOwnRequest() {
        var details = new RequestDetails(summary("09121234567"), List.of());
        when(queries.detailsForCustomer(CUSTOMER, details.summary().id())).thenReturn(Optional.of(details));

        assertThat(service.get(CUSTOMER, details.summary().id())).isSameAs(details);
    }

    @Test
    void aRequestOfAnotherCustomerOrNoneIsNotFound() {
        UUID id = UUID.randomUUID();
        when(queries.detailsForCustomer(CUSTOMER, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CUSTOMER, id)).isInstanceOf(RequestNotFoundException.class);
    }
}
