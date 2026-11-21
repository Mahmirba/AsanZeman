package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.RequestStatus;
import java.util.UUID;

/** What the agency gets back after registering: the request and its tracking code. */
public record RegisteredRequest(UUID id, String trackingCode, RequestStatus status) {
}
