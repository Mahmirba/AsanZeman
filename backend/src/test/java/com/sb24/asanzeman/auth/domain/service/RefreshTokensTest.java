package com.sb24.asanzeman.auth.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RefreshTokensTest {

    @Test
    void aTokenHas256RandomBitsAndIsUrlSafe() {
        String token = RefreshTokens.newToken();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void tokensDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(RefreshTokens.newToken());
        }

        assertThat(seen).hasSize(1000);
    }

    @Test
    void theHashIsTheSha256InHex() {
        // SHA-256("abc") from FIPS 180-2
        assertThat(RefreshTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void aMissingDigestAlgorithmFailsLoudly() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> RefreshTokens.digest("abc", "NO-SUCH-ALGORITHM"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NO-SUCH-ALGORITHM");
    }

    @Test
    void theHashIsStableAndDiffersPerToken() {
        assertThat(RefreshTokens.hash("a")).isEqualTo(RefreshTokens.hash("a")).hasSize(64)
                .isNotEqualTo(RefreshTokens.hash("b"));
    }
}
