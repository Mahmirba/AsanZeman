package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.RequestDetails;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import java.util.List;
import java.util.UUID;

/** The customer panel's view of the requests made for the customer who is logged in (Phase 2 exit). */
public interface CustomerRequestsUseCase {

    /** Newest first. */
    List<RequestSummary> list(String nationalCode);

    /** @throws com.sb24.asanzeman.request.domain.model.RequestNotFoundException unknown, or another customer's */
    RequestDetails get(String nationalCode, UUID requestId);
}
