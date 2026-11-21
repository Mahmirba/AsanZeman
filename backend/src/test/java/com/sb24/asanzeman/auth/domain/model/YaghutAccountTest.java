package com.sb24.asanzeman.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.DomainException;
import org.junit.jupiter.api.Test;

class YaghutAccountTest {

    @Test
    void theSessionIdIsNeverPrinted() {
        var account = new YaghutAccount("Agency", "9001", null, "SECRET", null, false, false);

        assertThat(account.toString()).contains("9001").doesNotContain("SECRET");
    }

    @Test
    void aSessionIdIsRequired() {
        assertThatThrownBy(() -> new YaghutAccount("A", "1", null, " ", null, false, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new YaghutAccount("A", "1", null, null, null, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theLoginRefusalsHaveStableCodesAndKinds() {
        assertThat(new InvalidYaghutCredentialsException().kind()).isEqualTo(DomainException.Kind.UNAUTHORIZED);
        assertThat(new SuspectedCustomerException().code()).isEqualTo("auth.yaghut.suspected-customer");
        assertThat(new YaghutAccessDeniedException().code()).isEqualTo("auth.yaghut.access-denied");
        assertThat(new TemporarySessionException().code()).isEqualTo("auth.yaghut.temporary-session");
        assertThat(new PasswordExpiredException().code()).isEqualTo("auth.yaghut.password-expired");
        assertThat(new AgencyNotRegisteredException().code()).isEqualTo("auth.agency.not-registered");
        assertThat(new YaghutSessionExpiredException().kind()).isEqualTo(DomainException.Kind.UNAUTHORIZED);
    }
}
