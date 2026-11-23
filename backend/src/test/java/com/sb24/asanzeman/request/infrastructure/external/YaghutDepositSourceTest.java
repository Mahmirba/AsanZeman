package com.sb24.asanzeman.request.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException;
import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import com.sb24.asanzeman.auth.domain.port.in.YaghutUserSessionUseCase;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutDeposit;
import com.sb24.asanzeman.integration.yaghut.YaghutException;
import com.sb24.asanzeman.integration.yaghut.YaghutSession;
import com.sb24.asanzeman.request.domain.model.CustomerDeposit;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YaghutDepositSourceTest {

    private static final UUID SESSION = UUID.randomUUID();
    private static final Instant END = Instant.parse("2026-11-23T12:00:00Z");
    private static final YaghutSession YAGHUT = new YaghutSession("JS-1", END);

    @Mock
    private YaghutUserSessionUseCase userSessions;
    @Mock
    private YaghutClient client;
    @InjectMocks
    private YaghutDepositSource source;

    private void sessionExists() {
        when(userSessions.require(SESSION)).thenReturn(new YaghutUserSession("JS-1", END));
    }

    @Test
    void readsTheDepositsWithTheCustomersOwnYaghutSession() {
        sessionExists();
        when(client.deposits(YAGHUT, "555")).thenReturn(List.of(new YaghutDeposit("D-1", "SHORT_ACCOUNT", 12L,
                "INDIVIDUAL", "OWNER_OF_DEPOSIT_AND_SIGNATURE", "OPEN", "IRR", "ACTUAL", new BigDecimal("100"))));

        assertThat(source.deposits(SESSION, "555")).containsExactly(new CustomerDeposit("D-1", "SHORT_ACCOUNT", 12L,
                "INDIVIDUAL", "OWNER_OF_DEPOSIT_AND_SIGNATURE", "OPEN", "IRR", "ACTUAL", new BigDecimal("100")));
    }

    @Test
    void anEndedYaghutSessionEndsTheAsanZemanSessionToo() {
        sessionExists();
        when(client.deposits(YAGHUT, "555"))
                .thenThrow(new YaghutException(YaghutException.Fault.INVALID_SESSION, "ended"));

        assertThatThrownBy(() -> source.deposits(SESSION, "555")).isInstanceOf(YaghutSessionExpiredException.class);
        verify(userSessions).end(SESSION);
    }

    @Test
    void aDeniedServiceIsReportedAsSuch() {
        sessionExists();
        when(client.deposits(YAGHUT, "555"))
                .thenThrow(new YaghutException(YaghutException.Fault.SERVICE_ACCESS_DENIED, "denied"));

        assertThatThrownBy(() -> source.deposits(SESSION, "555")).isInstanceOf(YaghutAccessDeniedException.class);
    }

    @Test
    void anyOtherFaultIsTechnical() {
        sessionExists();
        when(client.deposits(YAGHUT, "555"))
                .thenThrow(new YaghutException(YaghutException.Fault.INVALID_CUSTOMER, "none"));

        assertThatThrownBy(() -> source.deposits(SESSION, "555")).isInstanceOf(TemporarilyUnavailableException.class);
    }
}
