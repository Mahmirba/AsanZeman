package com.sb24.asanzeman.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.CaptchaFailedException;
import com.sb24.asanzeman.auth.domain.model.CaptchaResponse;
import com.sb24.asanzeman.auth.domain.model.Panel;
import com.sb24.asanzeman.auth.domain.port.out.CaptchaVerifierPort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerifyCaptchaServiceTest {

    @Mock
    private CaptchaVerifierPort verifier;

    private VerifyCaptchaService service() {
        return new VerifyCaptchaService(verifier);
    }

    @Test
    void validTokenPassesAndIsTrimmed() {
        when(verifier.verify(Panel.AGENCY, "tok")).thenReturn(new CaptchaResponse(true, List.of()));

        assertThatCode(() -> service().verify(Panel.AGENCY, "  tok ")).doesNotThrowAnyException();

        verify(verifier).verify(Panel.AGENCY, "tok");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankTokenIsRefusedWithoutCallingArcaptcha(String token) {
        assertThatThrownBy(() -> service().verify(Panel.BANK, token)).isInstanceOf(CaptchaFailedException.class);

        verifyNoInteractions(verifier);
    }

    @Test
    void wrongTokenAsksToSolveAgain() {
        when(verifier.verify(Panel.CUSTOMER, "tok"))
                .thenReturn(new CaptchaResponse(false, List.of("timeout-or-duplicate")));

        assertThatThrownBy(() -> service().verify(Panel.CUSTOMER, "tok")).isInstanceOf(CaptchaFailedException.class);
    }

    @Test
    void misconfigurationIsReportedAsTemporarilyUnavailable() {
        when(verifier.verify(Panel.BANK, "tok")).thenReturn(new CaptchaResponse(false, List.of("invalid-input-secret")));

        assertThatThrownBy(() -> service().verify(Panel.BANK, "tok"))
                .isInstanceOfSatisfying(TemporarilyUnavailableException.class, ex -> {
                    assertThat(ex.service()).isEqualTo("ARcaptcha");
                    assertThat(ex.getCause()).hasMessageContaining("invalid-input-secret");
                });
    }

    @Test
    void technicalFailureOfArcaptchaRefusesTheLogin() {
        var down = new TemporarilyUnavailableException("ARcaptcha", new RuntimeException("timeout"));
        when(verifier.verify(Panel.BANK, "tok")).thenThrow(down);

        assertThatThrownBy(() -> service().verify(Panel.BANK, "tok")).isSameAs(down);
    }
}
