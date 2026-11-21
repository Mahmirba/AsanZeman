package com.sb24.asanzeman.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * A throwaway RSA key pair, made fresh for every test JVM, so the Spring Boot integration tests never need a private
 * key in git (D13). Register it from a {@code @DynamicPropertySource} method.
 */
public final class TestKeys {

    private static final KeyPair PAIR = generate();

    private TestKeys() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("azs.security.jwt.private-key-pem", () -> pem("PRIVATE KEY", PAIR.getPrivate().getEncoded()));
        registry.add("azs.security.jwt.public-key-pem", () -> pem("PUBLIC KEY", PAIR.getPublic().getEncoded()));
    }

    public static KeyPair pair() {
        return PAIR;
    }

    public static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
                + "\n-----END " + type + "-----";
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
