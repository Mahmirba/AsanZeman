package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.Request;
import java.util.Optional;
import java.util.UUID;

/** The {@code request} and {@code request_companion} tables. */
public interface RequestRepositoryPort {

    /** Stores a new request with its companions; joins the caller's transaction. */
    void insert(Request request);

    Optional<Request> findById(UUID id);

    /** @return true when this agency registered a request for this customer at or after {@code since} (D47) */
    boolean existsSince(UUID agencyId, String customerCode, java.time.Instant since);
}
