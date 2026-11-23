package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.BranchUser;
import java.util.UUID;

/** A branch user's action on a request, with where it came from for the audit log. */
public record BranchCommand(BranchUser user, UUID requestId, String clientIp, String userAgent) {
}
