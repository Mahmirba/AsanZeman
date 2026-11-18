package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;

/** Rotates a refresh token (6, D7): every refresh returns a new pair and invalidates the old token. */
public interface RefreshSessionUseCase {

    /**
     * @throws com.sb24.asanzeman.auth.domain.model.InvalidSessionException 401 for an unknown, expired, revoked or
     *         already used token; using a used token again revokes the whole family
     */
    IssuedTokens refresh(String refreshToken, ClientInfo client);
}
