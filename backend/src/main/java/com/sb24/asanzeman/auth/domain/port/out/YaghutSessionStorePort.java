package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps a user's Yaghut session on the server, next to their Asan Zeman session (D7, 5.4): the panels never see the
 * cookie. One row per login, keyed by the session family.
 */
public interface YaghutSessionStorePort {

    /** @param expiresAt when Yaghut ends the session; {@code null} when unknown */
    void save(UUID familyId, String jsessionId, Instant expiresAt);

    Optional<YaghutUserSession> find(UUID familyId);

    void delete(UUID familyId);
}
