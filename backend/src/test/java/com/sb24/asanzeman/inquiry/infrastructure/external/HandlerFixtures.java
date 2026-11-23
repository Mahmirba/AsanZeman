package com.sb24.asanzeman.inquiry.infrastructure.external;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.inquiry.domain.model.CivilData;
import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryEndpoint;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/** What every inquiry handler test needs: a customer, a configured endpoint, and a client that answers as told. */
final class HandlerFixtures {

    static final CivilData MAN = new CivilData(Gender.MALE, LocalDate.of(1990, 5, 17)); // Jalali 1369/02/27
    static final InquiryContext CONTEXT = new InquiryContext("1000000011", "09121234567", 250_000_000L, "0123",
            LocalDate.of(1990, 5, 17), MAN);

    private HandlerFixtures() {
    }

    static InquiryEndpoint endpoint(Map<String, String> fields) {
        return new InquiryEndpoint(null, "/Svc/api/Check", Duration.ofSeconds(10), 1, fields);
    }

    /** The fixed fields of the services that take a tracking block (5.3). */
    static Map<String, String> trackingFields() {
        return Map.of("user-name", "azs", "ip", "10.0.0.1", "branch-code", "REQUEST", "channel-code", "3");
    }

    /** A client whose {@code post} and {@code get} answer with the given HTTP status and body. */
    static InquiryClient answering(InquiryEndpoint endpoint, int status, String body) {
        InquiryClient client = mock(InquiryClient.class);
        when(client.endpoint()).thenReturn(endpoint);
        when(client.post(any(), any())).thenReturn(new InquiryReply(status, body));
        when(client.get(any())).thenReturn(new InquiryReply(status, body));
        return client;
    }

    static InquiryOutcome failedBadResponse(InquiryType type) {
        return InquiryOutcome.failed(type, FailureKind.BAD_RESPONSE);
    }
}
