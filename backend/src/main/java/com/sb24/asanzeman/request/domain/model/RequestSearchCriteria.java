package com.sb24.asanzeman.request.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The tracking filters of SRS UC-AZS-13. Every filter is optional and they combine with AND. The agency is never
 * optional: an agency only ever sees its own requests.
 *
 * @param types          the request kinds to show (issuance, revocation); empty means both
 * @param name           part of the customer's full name
 * @param registeredFrom inclusive start of the registration time window
 * @param registeredTo   exclusive end of the window
 */
public record RequestSearchCriteria(UUID agencyId, String customerCode, String customerCif, Set<RequestType> types,
        String trackingCode, String name, Instant registeredFrom, Instant registeredTo) {

    public RequestSearchCriteria {
        if (agencyId == null) {
            throw new IllegalArgumentException("a search is always for one agency");
        }
        types = types == null ? Set.of() : Set.copyOf(types);
        if (registeredFrom != null && registeredTo != null && registeredTo.isBefore(registeredFrom)) {
            throw new InvalidRequestDataException("the end of the date range is before its start");
        }
    }
}
