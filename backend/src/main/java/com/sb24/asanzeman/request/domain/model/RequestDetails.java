package com.sb24.asanzeman.request.domain.model;

import java.util.List;

/** A request as the agency sees it when it opens the details (SRS UC-AZS-13, alternative flow 2). */
public record RequestDetails(RequestSummary summary, List<Companion> companions) {

    public RequestDetails {
        companions = List.copyOf(companions);
    }
}
