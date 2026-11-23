package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.RequestStatus;
import java.time.Instant;
import java.util.UUID;

/** What a branch action left behind: the new status, and who holds the request now, if anyone. */
public record ReviewResult(UUID requestId, RequestStatus status, String heldBy, Instant heldSince) {
}
