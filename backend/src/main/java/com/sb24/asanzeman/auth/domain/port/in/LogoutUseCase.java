package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import java.time.Instant;

/** Ends a session: the access token is revoked until it expires, and the refresh token family is revoked. */
public interface LogoutUseCase {

    /**
     * @param jti                  the access token's id
     * @param accessTokenExpiresAt when the access token expires on its own
     * @param subject              the caller, for the audit log
     * @param refreshToken         the refresh token to revoke with its family; may be {@code null}
     */
    void logout(String jti, Instant accessTokenExpiresAt, String subject, String refreshToken, ClientInfo client);
}
