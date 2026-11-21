package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.model.Session;
import java.time.Instant;
import java.util.UUID;

/** Starts a session for an already authenticated subject: issues the tokens and audits the login. */
public interface StartSessionUseCase {

    IssuedTokens start(Session session, ClientInfo client);

    /**
     * Same, for a session whose id the caller already knows (it keeps other data under it, such as the Yaghut
     * session) and that must not outlive {@code latestEnd}: the session end and the access token's end are both capped.
     *
     * @param latestEnd the latest moment the session may last to, or {@code null} for the normal length
     */
    IssuedTokens start(Session session, UUID familyId, Instant latestEnd, ClientInfo client);
}
