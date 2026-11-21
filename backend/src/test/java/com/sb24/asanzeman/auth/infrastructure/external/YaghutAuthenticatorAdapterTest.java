package com.sb24.asanzeman.auth.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.InvalidYaghutCredentialsException;
import com.sb24.asanzeman.auth.domain.model.SuspectedCustomerException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccount;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutException;
import com.sb24.asanzeman.integration.yaghut.YaghutLogin;
import com.sb24.asanzeman.integration.yaghut.YaghutSession;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YaghutAuthenticatorAdapterTest {

    private static final Instant END = Instant.parse("2026-11-17T11:00:00Z");

    @Mock
    private YaghutClient client;

    private YaghutAccount account(String temporaryId, String expiration) {
        when(client.login("u", "p")).thenReturn(new YaghutLogin(new YaghutSession("S1", END), "Agency", "9001",
                "0012345678", temporaryId, null, expiration));
        return adapter().login("u", "p");
    }

    private YaghutAuthenticatorAdapter adapter() {
        return new YaghutAuthenticatorAdapter(client);
    }

    @Test
    void mapsALoginToTheDomainAccount() {
        var account = account(null, null);

        assertThat(account.cif()).isEqualTo("9001");
        assertThat(account.code()).isEqualTo("0012345678");
        assertThat(account.name()).isEqualTo("Agency");
        assertThat(account.sessionId()).isEqualTo("S1");
        assertThat(account.sessionExpiresAt()).isEqualTo(END);
        assertThat(account.temporarySession()).isFalse();
        assertThat(account.passwordExpired()).isFalse();
    }

    @Test
    void aTemporarySessionIdMarksATemporarySession() {
        assertThat(account("T1", null).temporarySession()).isTrue();
        assertThat(account(" ", null).temporarySession()).isFalse();
    }

    @Test
    void anExpiredFlagMarksAnExpiredPassword() {
        assertThat(account(null, "{\"expired\":true}").passwordExpired()).isTrue();
        assertThat(account(null, "{\"expired\":false}").passwordExpired()).isFalse();
        assertThat(account(null, "{}").passwordExpired()).isFalse();
        assertThat(account(null, "not json").passwordExpired()).isFalse();
    }

    @Test
    void businessFaultsBecomeLoginRefusals() {
        doThrow(new YaghutException(YaghutException.Fault.INVALID_CREDENTIALS, "m")).when(client).login("u", "p");
        assertThatThrownBy(() -> adapter().login("u", "p")).isInstanceOf(InvalidYaghutCredentialsException.class);

        doThrow(new YaghutException(YaghutException.Fault.SUSPECTED_CUSTOMER, "m")).when(client).login("u", "p");
        assertThatThrownBy(() -> adapter().login("u", "p")).isInstanceOf(SuspectedCustomerException.class);

        doThrow(new YaghutException(YaghutException.Fault.SERVICE_ACCESS_DENIED, "m")).when(client).login("u", "p");
        assertThatThrownBy(() -> adapter().login("u", "p")).isInstanceOf(YaghutAccessDeniedException.class);
    }

    @Test
    void anyOtherFaultIsTechnical() {
        doThrow(new YaghutException(YaghutException.Fault.INVALID_SESSION, "m")).when(client).login("u", "p");

        assertThatThrownBy(() -> adapter().login("u", "p")).isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void technicalFailuresPassThrough() {
        var down = new TemporarilyUnavailableException("Yaghut", new RuntimeException("timeout"));
        doThrow(down).when(client).login("u", "p");

        assertThatThrownBy(() -> adapter().login("u", "p")).isSameAs(down);
    }
}
