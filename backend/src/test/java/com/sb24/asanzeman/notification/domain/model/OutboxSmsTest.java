package com.sb24.asanzeman.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.notification.domain.port.in.SmsTemplate;
import com.sb24.asanzeman.shared.model.DomainException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OutboxSmsTest {

    private static OutboxSms sms(String mobile, String text) {
        return new OutboxSms(UUID.randomUUID(), mobile, "REQUEST_REGISTERED", text, null, 0);
    }

    @Test
    void theGatewayNumberDropsTheLeadingZeroAndAddsNineEight() {
        assertThat(sms("09121234567", "x").gatewayNumber()).isEqualTo("0989121234567");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"9121234567", "0912123456", "091212345678", "08121234567", "0912123456a", " 09121234567"})
    void onlyZeroNineAndNineDigitsIsAMobile(String mobile) {
        assertThatThrownBy(() -> sms(mobile, "x")).isInstanceOf(InvalidMobileException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aMessageNeedsATextAndABlankOneIsAProgrammingError(String text) {
        assertThatThrownBy(() -> sms("09121234567", text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anInvalidMobileIsABadRequest() {
        assertThat(new InvalidMobileException().kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
    }

    @Test
    void theTemplateKeysPointAtTheMessageConfig() {
        assertThat(SmsTemplate.REQUEST_REGISTERED.messageKey()).isEqualTo("sms.request-registered");
        assertThat(SmsStatus.valueOf("DEAD")).isEqualTo(SmsStatus.DEAD);
        assertThat(new SmsRejectedException("x").getMessage()).isEqualTo("x");
        assertThat(new SmsCredentialsException("y").getMessage()).isEqualTo("y");
    }
}
