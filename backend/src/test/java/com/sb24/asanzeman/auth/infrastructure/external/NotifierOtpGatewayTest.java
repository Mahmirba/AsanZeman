package com.sb24.asanzeman.auth.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.integration.notifier.NotifierClient;
import com.sb24.asanzeman.integration.notifier.NotifierCredentialsException;
import com.sb24.asanzeman.integration.notifier.NotifierRejectedException;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotifierOtpGatewayTest {

    @Mock
    private NotifierClient client;
    @InjectMocks
    private NotifierOtpGateway gateway;

    @Test
    void sendsTheCodeThroughTheNotifierAndReturnsItsId() {
        when(client.sendOtp("09121234567")).thenReturn("otp-1");

        assertThat(gateway.send("09121234567")).isEqualTo("otp-1");
    }

    @Test
    void checksTheCodeThroughTheNotifier() {
        when(client.validateOtp("09121234567", "otp-1", "1234")).thenReturn(true);
        when(client.validateOtp("09121234567", "otp-1", "0000")).thenReturn(false);

        assertThat(gateway.validate("09121234567", "otp-1", "1234")).isTrue();
        assertThat(gateway.validate("09121234567", "otp-1", "0000")).isFalse();
    }

    @Test
    void aRefusedSendIsOurProblemSoTheCustomerIsToldToTryLater() {
        when(client.sendOtp("1")).thenThrow(new NotifierRejectedException("bad number"));
        when(client.sendOtp("2")).thenThrow(new NotifierCredentialsException("403"));

        assertThatThrownBy(() -> gateway.send("1")).isInstanceOf(TemporarilyUnavailableException.class);
        assertThatThrownBy(() -> gateway.send("2")).isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aRefusedCheckIsOurProblemTooNeverAWrongCode() {
        when(client.validateOtp("09121234567", "a", "1")).thenThrow(new NotifierRejectedException("bad id"));
        when(client.validateOtp("09121234567", "b", "1")).thenThrow(new NotifierCredentialsException("403"));

        assertThatThrownBy(() -> gateway.validate("09121234567", "a", "1")).isInstanceOf(TemporarilyUnavailableException.class);
        assertThatThrownBy(() -> gateway.validate("09121234567", "b", "1")).isInstanceOf(TemporarilyUnavailableException.class);
    }
}
