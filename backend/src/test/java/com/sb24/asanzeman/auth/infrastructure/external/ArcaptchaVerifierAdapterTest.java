package com.sb24.asanzeman.auth.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.CaptchaResponse;
import com.sb24.asanzeman.auth.domain.model.Panel;
import com.sb24.asanzeman.integration.arcaptcha.ArcaptchaClient;
import com.sb24.asanzeman.integration.arcaptcha.ArcaptchaVerifyResponse;
import com.sb24.asanzeman.integration.arcaptcha.config.ArcaptchaProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArcaptchaVerifierAdapterTest {

    @Mock
    private ArcaptchaClient client;

    private static ArcaptchaProperties properties(boolean enabled) {
        return new ArcaptchaProperties("https://captcha.test/verify", enabled,
                new ArcaptchaProperties.Keys("bank-site", "bank-secret"),
                new ArcaptchaProperties.Keys("agency-site", "agency-secret"),
                new ArcaptchaProperties.Keys("customer-site", "customer-secret"),
                Duration.ofSeconds(2), Duration.ofSeconds(5), new ArcaptchaProperties.Tls(false));
    }

    @ParameterizedTest
    @CsvSource({"BANK,bank-site,bank-secret", "AGENCY,agency-site,agency-secret",
            "CUSTOMER,customer-site,customer-secret"})
    void usesTheKeysOfThePanel(Panel panel, String siteKey, String secretKey) {
        when(client.verify(siteKey, secretKey, "tok")).thenReturn(new ArcaptchaVerifyResponse(true, List.of()));

        CaptchaResponse response = new ArcaptchaVerifierAdapter(client, properties(true)).verify(panel, "tok");

        assertThat(response.success()).isTrue();
        verify(client).verify(siteKey, secretKey, "tok");
    }

    @Test
    void passesTheErrorCodesOn() {
        when(client.verify("bank-site", "bank-secret", "tok"))
                .thenReturn(new ArcaptchaVerifyResponse(false, List.of("invalid-input-response")));

        CaptchaResponse response = new ArcaptchaVerifierAdapter(client, properties(true)).verify(Panel.BANK, "tok");

        assertThat(response.success()).isFalse();
        assertThat(response.errorCodes()).containsExactly("invalid-input-response");
    }

    @Test
    void acceptsWithoutCallingWhenVerificationIsOff() {
        CaptchaResponse response = new ArcaptchaVerifierAdapter(client, properties(false)).verify(Panel.BANK, "tok");

        assertThat(response.success()).isTrue();
        verifyNoInteractions(client);
    }
}
