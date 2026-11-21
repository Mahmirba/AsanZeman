package com.sb24.asanzeman.integration.yaghut;

import java.time.Clock;
import java.time.Instant;

/**
 * A Yaghut login session: the {@code JSESSIONID} cookie and the time Yaghut says it ends (5.4, D35). The
 * session id is never shown to a panel. {@code expiresAt} may be {@code null} when Yaghut gave no end time.
 */
public record YaghutSession(String jsessionId, Instant expiresAt) {

    public boolean expired(Clock clock) {
        return expiresAt != null && !clock.instant().isBefore(expiresAt);
    }
}
