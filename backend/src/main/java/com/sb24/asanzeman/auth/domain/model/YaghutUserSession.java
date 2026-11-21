package com.sb24.asanzeman.auth.domain.model;

import java.time.Instant;

/**
 * A user's own Yaghut session as kept with their Asan Zeman session (D7, D35). {@code jsessionId} is a secret: it
 * never goes to a panel and is not printed. {@code expiresAt} may be {@code null} when Yaghut gave no end time.
 */
public record YaghutUserSession(String jsessionId, Instant expiresAt) {

    public boolean expired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "YaghutUserSession[expiresAt=" + expiresAt + "]";
    }
}
