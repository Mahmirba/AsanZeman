package com.sb24.asanzeman.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretsGuardTest {

    @Test
    @DisplayName("lists every key still set to CHANGE_ME, sorted, ignoring case and whitespace")
    void listsUnsetSecrets() {
        Map<String, String> props = new HashMap<>();
        props.put("azs.integration.yaghut.technical-user.password", "CHANGE_ME");
        props.put("azs.integration.gateway.client-secret", " change_me ");
        props.put("azs.integration.notifier.user", "azs-user");
        props.put("azs.otp.validity", null);

        assertThat(SecretsGuard.unsetSecrets(props))
                .containsExactly("azs.integration.gateway.client-secret",
                        "azs.integration.yaghut.technical-user.password");
    }

    @Test
    @DisplayName("verify passes when nothing is CHANGE_ME")
    void verifyPassesWhenAllSet() {
        SecretsGuard.verify(Map.of("azs.integration.notifier.pass", "s3cret"));
    }

    @Test
    @DisplayName("verify fails naming every unset key")
    void verifyFailsNamingKeys() {
        assertThatThrownBy(() -> SecretsGuard.verify(Map.of(
                "spring.datasource.password", "CHANGE_ME",
                "azs.security.jwt.private-key-pem", "CHANGE_ME")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("azs.security.jwt.private-key-pem")
                .hasMessageContaining("spring.datasource.password");
    }
}
