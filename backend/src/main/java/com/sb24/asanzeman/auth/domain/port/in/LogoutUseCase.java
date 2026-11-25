package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import java.time.Instant;
import java.util.UUID;

/**
 * Ends a session: the access token is revoked until it expires, and every refresh token of the session is revoked, so
 * no copy of the refresh token can start a new access token.
 */
public interface LogoutUseCase {

    /**
     * @param jti                  the access token's id
     * @param accessTokenExpiresAt when the access token expires on its own
     * @param subject              the caller, for the audit log
     * @param familyId             the session ({@code sid} claim of the access token); {@code null} for a token without one
     */
    void logout(String jti, Instant accessTokenExpiresAt, String subject, UUID familyId, ClientInfo client);
}
