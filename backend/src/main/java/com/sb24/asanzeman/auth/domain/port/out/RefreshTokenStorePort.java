package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.RefreshTokenRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Where refresh tokens are kept (only their hashes). */
public interface RefreshTokenStorePort {

    void save(RefreshTokenRecord token);

    Optional<RefreshTokenRecord> findByHash(String tokenHash);

    /**
     * Marks the token used, but only if nobody did before and it is not revoked: one atomic step, so two concurrent
     * uses of the same token cannot both win.
     *
     * @return {@code true} if this call marked it
     */
    boolean markUsed(UUID id, Instant at);

    /** Revokes every token of the family that is not revoked yet. */
    void revokeFamily(UUID familyId, Instant at);

    /** Deletes the tokens that expired before {@code before}. @return how many */
    int deleteExpiredBefore(Instant before);
}
