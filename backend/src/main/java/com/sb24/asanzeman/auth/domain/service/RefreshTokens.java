package com.sb24.asanzeman.auth.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Makes refresh tokens and hashes them for storage (6, D7): 256 random bits, only the SHA-256 hash is kept. */
public final class RefreshTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BYTES = 32;

    private RefreshTokens() {
    }

    /** A new random token, URL-safe, without padding. */
    public static String newToken() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The SHA-256 of the token, as 64 lowercase hex characters. */
    public static String hash(String token) {
        return digest(token, "SHA-256");
    }

    static String digest(String token, String algorithm) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm).digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is required but not available", e);
        }
    }
}
