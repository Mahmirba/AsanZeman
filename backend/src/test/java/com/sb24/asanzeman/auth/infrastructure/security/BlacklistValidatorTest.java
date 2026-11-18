package com.sb24.asanzeman.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class BlacklistValidatorTest {

    private final JwtBlacklistPort blacklist = mock(JwtBlacklistPort.class);
    private final BlacklistValidator validator = new BlacklistValidator(blacklist);

    private static Jwt jwt(String jti) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("ali")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (jti != null) {
            builder.jti(jti);
        }
        return builder.build();
    }

    @Test
    void aTokenThatWasNotRevokedPasses() {
        when(blacklist.isRevoked("ok")).thenReturn(false);

        assertThat(validator.validate(jwt("ok")).hasErrors()).isFalse();
    }

    @Test
    void aRevokedTokenFails() {
        when(blacklist.isRevoked("gone")).thenReturn(true);

        var result = validator.validate(jwt("gone"));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).extracting(e -> e.getErrorCode()).containsExactly("invalid_token");
    }

    @Test
    void aTokenWithoutAnIdFailsWithoutAskingTheBlacklist() {
        assertThat(validator.validate(jwt(null)).hasErrors()).isTrue();

        verifyNoInteractions(blacklist);
    }
}
