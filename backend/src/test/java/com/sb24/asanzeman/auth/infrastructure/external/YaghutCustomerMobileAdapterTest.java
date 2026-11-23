package com.sb24.asanzeman.auth.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.SuspectedCustomerException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccount;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutCustomer;
import com.sb24.asanzeman.integration.yaghut.YaghutException;
import com.sb24.asanzeman.integration.yaghut.YaghutSession;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YaghutCustomerMobileAdapterTest {

    private static final Instant END = Instant.parse("2026-11-23T11:00:00Z");
    private static final YaghutAccount ACCOUNT = new YaghutAccount("Ali", null, "1000000011", "JS-1", END, false,
            false);
    private static final YaghutSession SESSION = new YaghutSession("JS-1", END);

    @Mock
    private YaghutClient client;
    @InjectMocks
    private YaghutCustomerMobileAdapter adapter;

    private static YaghutCustomer customer(String mobile) {
        return new YaghutCustomer("555", "1000000011", "Ali", "Rezaei", "Ali Rezaei", "Reza", null, mobile, null, null,
                null, "ACTUAL", "MALE");
    }

    @Test
    void readsTheMobileWithTheCustomersOwnSession() {
        when(client.customerDetailInfo(SESSION, "1000000011")).thenReturn(customer(" 09121234567 "));

        assertThat(adapter.mobileOf(ACCOUNT, "1000000011")).contains("09121234567");
    }

    @Test
    void noMobileAtYaghutMeansEmpty() {
        when(client.customerDetailInfo(SESSION, "1000000011")).thenReturn(customer(null), customer(" "));

        assertThat(adapter.mobileOf(ACCOUNT, "1000000011")).isEmpty();
        assertThat(adapter.mobileOf(ACCOUNT, "1000000011")).isEmpty();
    }

    @Test
    void aMobileThatIsNotInTheFormTheSmsCentreTakesIsTreatedAsMissing() {
        when(client.customerDetailInfo(SESSION, "1000000011")).thenReturn(customer("+989121234567"),
                customer("9121234567"), customer("0912123456"));

        assertThat(adapter.mobileOf(ACCOUNT, "1000000011")).isEmpty();
        assertThat(adapter.mobileOf(ACCOUNT, "1000000011")).isEmpty();
        assertThat(adapter.mobileOf(ACCOUNT, "1000000011")).isEmpty();
    }

    @Test
    void aUserWhoIsNotABankCustomerIsRefused() {
        when(client.customerDetailInfo(SESSION, "1000000011"))
                .thenThrow(new YaghutException(YaghutException.Fault.INVALID_CUSTOMER, "not a customer"));

        assertThatThrownBy(() -> adapter.mobileOf(ACCOUNT, "1000000011"))
                .isInstanceOf(SuspectedCustomerException.class);
    }

    @Test
    void aDeniedServiceIsReportedAsSuch() {
        when(client.customerDetailInfo(SESSION, "1000000011"))
                .thenThrow(new YaghutException(YaghutException.Fault.SERVICE_ACCESS_DENIED, "denied"));

        assertThatThrownBy(() -> adapter.mobileOf(ACCOUNT, "1000000011"))
                .isInstanceOf(YaghutAccessDeniedException.class);
    }

    @Test
    void anyOtherFaultIsTechnical() {
        when(client.customerDetailInfo(SESSION, "1000000011"))
                .thenThrow(new YaghutException(YaghutException.Fault.INVALID_SESSION, "ended"));

        assertThatThrownBy(() -> adapter.mobileOf(ACCOUNT, "1000000011"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }
}
