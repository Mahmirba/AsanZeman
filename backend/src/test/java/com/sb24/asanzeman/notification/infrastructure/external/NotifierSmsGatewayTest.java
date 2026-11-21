package com.sb24.asanzeman.notification.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.integration.notifier.NotifierClient;
import com.sb24.asanzeman.integration.notifier.NotifierCredentialsException;
import com.sb24.asanzeman.integration.notifier.NotifierRejectedException;
import com.sb24.asanzeman.notification.domain.model.SmsCredentialsException;
import com.sb24.asanzeman.notification.domain.model.SmsRejectedException;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotifierSmsGatewayTest {

    @Mock
    private NotifierClient client;

    @Test
    void returnsTheSmsId() {
        when(client.sendSimpleSms("0989121234567", "hello")).thenReturn("SMS-1");

        assertThat(new NotifierSmsGateway(client).send("0989121234567", "hello")).isEqualTo("SMS-1");
    }

    @Test
    void a400BecomesARejection() {
        doThrow(new NotifierRejectedException("bad")).when(client).sendSimpleSms("1", "x");

        assertThatThrownBy(() -> new NotifierSmsGateway(client).send("1", "x"))
                .isInstanceOf(SmsRejectedException.class).hasMessage("bad");
    }

    @Test
    void a403BecomesACredentialsProblem() {
        doThrow(new NotifierCredentialsException("contact support")).when(client).sendSimpleSms("1", "x");

        assertThatThrownBy(() -> new NotifierSmsGateway(client).send("1", "x"))
                .isInstanceOf(SmsCredentialsException.class).hasMessage("contact support");
    }

    @Test
    void technicalFailuresPassThrough() {
        var down = new TemporarilyUnavailableException("Notifier", new RuntimeException("timeout"));
        doThrow(down).when(client).sendSimpleSms("1", "x");

        assertThatThrownBy(() -> new NotifierSmsGateway(client).send("1", "x")).isSameAs(down);
    }
}
