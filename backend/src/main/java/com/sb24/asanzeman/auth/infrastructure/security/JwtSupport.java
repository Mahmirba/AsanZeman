package com.sb24.asanzeman.auth.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Builds the RS256 {@link JwtEncoder} and {@link JwtDecoder} from the configured key pair (D13). */
public final class JwtSupport {

    private JwtSupport() {
    }

    public static JwtEncoder encoder(String privateKeyPem, String publicKeyPem) {
        RSAPrivateKey privateKey = JwtKeys.privateKey(privateKeyPem);
        RSAPublicKey publicKey = JwtKeys.publicKey(publicKeyPem);
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(NimbusAccessTokenIssuer.KEY_ID)
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /**
     * Accepts only RS256 tokens signed with our key, from our issuer, not expired (with Spring's default clock skew),
     * and not revoked at logout.
     */
    public static JwtDecoder decoder(String publicKeyPem, String issuer, JwtBlacklistPort blacklist) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(JwtKeys.publicKey(publicKeyPem))
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new BlacklistValidator(blacklist)));
        return decoder;
    }
}
