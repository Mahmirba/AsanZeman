package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.shared.model.Rials;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Requests for the persistence tests. */
final class RequestRepositoryFixtures {

    private RequestRepositoryFixtures() {
    }

    static Request request(UUID agency, TrackingCode code, List<Companion> companions, Instant at) {
        return new Request(UUID.randomUUID(), code, RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY, agency,
                "0123", Rials.of(250_000_000), LocalDate.of(2027, 1, 20),
                new CustomerSnapshot("555", "1000000011", "Ali", "Rezaei", "Ali Rezaei", "Reza",
                        LocalDate.of(1990, 5, 17), "09121234567", "123", "S", "T", "ACTUAL", "MALE"),
                companions, "Sky Travel", at);
    }

    /** Only the required details of the customer. */
    static Request bare(UUID agency, TrackingCode code, Instant at) {
        return new Request(UUID.randomUUID(), code, RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY, agency,
                "0123", Rials.of(1), LocalDate.of(2027, 1, 20),
                new CustomerSnapshot(null, "1000000011", null, null, null, null, null, null, null, null, null, null,
                        null),
                List.of(), "Sky Travel", at);
    }

    static Request withAmount(Request r, Rials amount) {
        return new Request(r.id(), r.trackingCode(), r.type(), r.status(), r.agencyId(), r.branchCode(), amount,
                r.dueDate(), r.customer(), r.companions(), r.registeredBy(), r.registeredAt());
    }
}
