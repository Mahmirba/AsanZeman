package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import java.util.UUID;

/**
 * The agency's or customer's own Yaghut session (5.4, D35), found by the {@code sid} claim of their access token.
 * Other modules use it to call Yaghut on the user's behalf.
 */
public interface YaghutUserSessionUseCase {

    /**
     * @throws com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException there is none, or its end has passed;
     *         an ended session is removed
     */
    YaghutUserSession require(UUID sessionId);

    /** Ends the Asan Zeman session and drops its Yaghut session, after Yaghut said the session is invalid. */
    void end(UUID sessionId);
}
