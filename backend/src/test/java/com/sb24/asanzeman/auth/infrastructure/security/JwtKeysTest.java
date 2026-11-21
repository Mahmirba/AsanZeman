package com.sb24.asanzeman.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.support.TestKeys;
import org.junit.jupiter.api.Test;

class JwtKeysTest {

    private static final String PRIVATE = TestKeys.pem("PRIVATE KEY", TestKeys.pair().getPrivate().getEncoded());
    private static final String PUBLIC = TestKeys.pem("PUBLIC KEY", TestKeys.pair().getPublic().getEncoded());

    @Test
    void readsBothKeysFromPem() {
        assertThat(JwtKeys.privateKey(PRIVATE).getModulus()).isEqualTo(JwtKeys.publicKey(PUBLIC).getModulus());
        assertThat(JwtKeys.publicKey(PUBLIC).getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void lineBreaksMayBeWrittenAsBackslashN() {
        // one line, as it is written in application.properties or an environment variable
        String oneLine = PRIVATE.replace("\n", "\\n");

        assertThat(oneLine).doesNotContain("\n");
        assertThat(JwtKeys.privateKey(oneLine).getModulus()).isEqualTo(JwtKeys.publicKey(PUBLIC).getModulus());
    }

    @Test
    void theHeaderAndFooterAreOptional() {
        String bare = PUBLIC.replaceAll("-----[A-Z ]+-----", "");

        assertThat(JwtKeys.publicKey(bare).getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void garbageIsRefusedWithAClearMessage() {
        assertThatThrownBy(() -> JwtKeys.privateKey("CHANGE_ME")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("azs.security.jwt.private-key-pem").hasMessageContaining("PKCS#8");
        assertThatThrownBy(() -> JwtKeys.publicKey("not base64 !!!")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("azs.security.jwt.public-key-pem").hasMessageContaining("X.509");
    }

    @Test
    void aKeyOfTheWrongKindIsRefused() {
        assertThatThrownBy(() -> JwtKeys.privateKey(PUBLIC)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtKeys.publicKey(PRIVATE)).isInstanceOf(IllegalStateException.class);
    }
}
