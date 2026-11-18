package com.sb24.asanzeman.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.shared.model.DomainException;
import org.junit.jupiter.api.Test;

class CaptchaFailedExceptionTest {

    @Test
    void isABadRequestWithAStableCode() {
        var ex = new CaptchaFailedException();

        assertThat(ex.code()).isEqualTo("auth.captcha-invalid").isEqualTo(CaptchaFailedException.CODE);
        assertThat(ex.kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
        assertThat(ex.args()).isEmpty();
    }

    @Test
    void panelsAreBankAgencyCustomer() {
        assertThat(Panel.values()).containsExactly(Panel.BANK, Panel.AGENCY, Panel.CUSTOMER);
    }
}
