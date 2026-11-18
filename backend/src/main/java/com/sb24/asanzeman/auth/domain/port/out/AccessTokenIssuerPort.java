package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.Session;
import java.time.Instant;

/** Signs access tokens (RS256 JWT: {@code sub}, {@code role}, {@code branch_code}, {@code iat}, {@code exp}). */
public interface AccessTokenIssuerPort {

    /** @return the signed, compact token */
    String issue(Session session, String jti, Instant issuedAt, Instant expiresAt);
}
