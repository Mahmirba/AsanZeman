package com.sb24.asanzeman.auth.infrastructure.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads the RS256 key pair from the configuration (D13, D36): a PKCS#8 private key
 * ({@code -----BEGIN PRIVATE KEY-----}) and an X.509 public key ({@code -----BEGIN PUBLIC KEY-----}). Line breaks may
 * be real or written as the two characters {@code \n}, so the value also fits on one line of a properties file.
 */
public final class JwtKeys {

    private JwtKeys() {
    }

    public static RSAPrivateKey privateKey(String pem) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der(pem)));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException(
                    "azs.security.jwt.private-key-pem is not a PKCS#8 RSA private key (-----BEGIN PRIVATE KEY-----)", e);
        }
    }

    public static RSAPublicKey publicKey(String pem) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der(pem)));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException(
                    "azs.security.jwt.public-key-pem is not an X.509 RSA public key (-----BEGIN PUBLIC KEY-----)", e);
        }
    }

    private static byte[] der(String pem) {
        String body = pem.replace("\\n", "\n").replaceAll("-----(BEGIN|END)[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
