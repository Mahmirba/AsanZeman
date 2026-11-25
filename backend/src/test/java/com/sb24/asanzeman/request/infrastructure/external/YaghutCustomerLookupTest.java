package com.sb24.asanzeman.request.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException;
import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import com.sb24.asanzeman.auth.domain.port.in.YaghutUserSessionUseCase;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutCustomer;
import com.sb24.asanzeman.integration.yaghut.YaghutException;
import com.sb24.asanzeman.integration.yaghut.YaghutSession;
import com.sb24.asanzeman.request.domain.model.NotBankCustomerException;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YaghutCustomerLookupTest {

    private static final UUID SESSION = UUID.randomUUID();
    private static final Instant END = Instant.parse("2026-11-21T12:00:00Z");
    private static final YaghutSession YAGHUT = new YaghutSession("S1", END);

    @Mock
    private YaghutUserSessionUseCase userSessions;
    @Mock
    private YaghutClient client;

    private YaghutCustomerLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new YaghutCustomerLookup(userSessions, client);
        Mockito.lenient().when(userSessions.require(SESSION)).thenReturn(new YaghutUserSession("S1", END));
    }

    @Test
    void callsYaghutWithTheAgenciesOwnSessionAndMapsTheSnapshot() {
        when(client.customerDetailInfo(YAGHUT, "1000000011")).thenReturn(new YaghutCustomer("555", "1000000011",
                "Ali", "Rezaei", "Ali Rezaei", "Reza", LocalDate.of(1990, 5, 17), "09121234567", "123", "S", "T",
                "ACTUAL", "MALE"));

        var customer = lookup.find(SESSION, "1000000011");

        assertThat(customer.cif()).isEqualTo("555");
        assertThat(customer.code()).isEqualTo("1000000011");
        assertThat(customer.fullName()).isEqualTo("Ali Rezaei");
        assertThat(customer.birthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(customer.mobile()).isEqualTo("09121234567");
        assertThat(customer.personality()).isEqualTo("ACTUAL");
        assertThat(customer.gender()).isEqualTo("MALE");
    }

    @Test
    void aMissingCodeInTheAnswerFallsBackToTheCodeAsked() {
        when(client.customerDetailInfo(YAGHUT, "1000000011")).thenReturn(new YaghutCustomer(null, null, null, null,
                null, null, null, null, null, null, null, "ACTUAL", null));

        assertThat(lookup.find(SESSION, "1000000011").code()).isEqualTo("1000000011");
    }

    @Test
    void anUnknownCustomerIsNotABankCustomer() {
        when(client.customerDetailInfo(YAGHUT, "1000000011"))
                .thenThrow(new YaghutException(YaghutException.Fault.INVALID_CUSTOMER, "none"));

        assertThatThrownBy(() -> lookup.find(SESSION, "1000000011")).isInstanceOf(NotBankCustomerException.class);
    }

    @Test
    void anEndedYaghutSessionEndsTheAsanZemanSessionToo() {
        when(client.customerDetailInfo(YAGHUT, "1000000011"))
                .thenThrow(new YaghutException(YaghutException.Fault.INVALID_SESSION, "gone"));

        assertThatThrownBy(() -> lookup.find(SESSION, "1000000011")).isInstanceOf(YaghutSessionExpiredException.class);
        verify(userSessions).end(SESSION);
    }

    @Test
    void anInactiveServiceIsAccessDenied() {
        when(client.customerDetailInfo(YAGHUT, "1000000011"))
                .thenThrow(new YaghutException(YaghutException.Fault.SERVICE_ACCESS_DENIED, "no"));

        assertThatThrownBy(() -> lookup.find(SESSION, "1000000011")).isInstanceOf(YaghutAccessDeniedException.class);
    }

    @Test
    void anyOtherFaultIsTechnical() {
        when(client.customerDetailInfo(YAGHUT, "1000000011"))
                .thenThrow(new YaghutException(YaghutException.Fault.INVALID_CREDENTIALS, "odd"));

        assertThatThrownBy(() -> lookup.find(SESSION, "1000000011"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void technicalFailuresPassThrough() {
        var down = new TemporarilyUnavailableException("Yaghut", new RuntimeException("timeout"));
        when(client.customerDetailInfo(YAGHUT, "1000000011")).thenThrow(down);

        assertThatThrownBy(() -> lookup.find(SESSION, "1000000011")).isSameAs(down);
        verify(userSessions, Mockito.never()).end(SESSION);
    }

    @Test
    void aMissingYaghutSessionStopsBeforeTheCall() {
        doThrow(new YaghutSessionExpiredException()).when(userSessions).require(SESSION);

        assertThatThrownBy(() -> lookup.find(SESSION, "1000000011")).isInstanceOf(YaghutSessionExpiredException.class);
        verifyNoInteractions(client);
    }
}
