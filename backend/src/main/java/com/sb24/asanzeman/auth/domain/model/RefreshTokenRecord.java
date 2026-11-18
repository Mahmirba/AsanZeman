package com.sb24.asanzeman.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One stored refresh token (6, D7). All tokens of one login share a {@code familyId}. A token is used once: asking
 * again with a used token is reuse, and the whole family is revoked. {@code expiresAt} is the end of the session and
 * does not move when the token is rotated.
 *
 * @param tokenHash SHA-256 of the token, in hex; the token itself is never stored
 */
public record RefreshTokenRecord(UUID id, UUID familyId, Session session, String tokenHash, Instant expiresAt,
        Instant usedAt, Instant revokedAt) {

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean revoked() {
        return revokedAt != null;
    }

    public boolean used() {
        return usedAt != null;
    }
}
