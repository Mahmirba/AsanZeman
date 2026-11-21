package com.sb24.asanzeman.auth.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code azs.security.jwt.*} (D5, D13, D36). The key pair is PEM text: a PKCS#8 private key and an X.509 public key.
 * The private key is a secret and stays {@code CHANGE_ME} in git.
 */
@ConfigurationProperties("azs.security.jwt")
public record JwtProperties(
        String issuer,
        String privateKeyPem,
        String publicKeyPem,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration customerSessionTtl) {
}
