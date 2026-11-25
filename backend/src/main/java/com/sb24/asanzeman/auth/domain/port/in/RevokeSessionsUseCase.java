package com.sb24.asanzeman.auth.domain.port.in;

import java.util.UUID;

/**
 * Ends every session of a user or an agency at once, for the case where they must no longer be in the system, such as an
 * agency the admin switched off. Their refresh tokens stop working at once; an access token already issued lasts
 * until it expires, at most 15 minutes.
 */
public interface RevokeSessionsUseCase {

    void revokeAllOf(UUID subjectId);
}
