package com.sb24.asanzeman.auth.domain.port.out;

import java.time.Instant;

/** Access tokens revoked before they expire (logout). A token only needs to be listed until {@code expiresAt}. */
public interface JwtBlacklistPort {

    /** Revokes the token; revoking it again is harmless. */
    void revoke(String jti, Instant expiresAt, String reason);

    boolean isRevoked(String jti);

    /** Deletes the entries whose token has expired by {@code now}: they can no longer be used, so listing them is pointless. @return how many */
    int deleteExpired(Instant now);
}
