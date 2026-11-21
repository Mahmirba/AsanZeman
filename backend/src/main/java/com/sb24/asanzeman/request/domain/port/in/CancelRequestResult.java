package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.CancelOutcome;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import java.util.UUID;

/** What the action did, so the panel can show the right message (4.2). */
public record CancelRequestResult(UUID requestId, CancelOutcome outcome, RequestStatus status) {
}
