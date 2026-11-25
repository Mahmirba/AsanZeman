package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The {@code request} and {@code request_companion} tables. */
public interface RequestRepositoryPort {

    /** Stores a new request with its companions; joins the caller's transaction. */
    void insert(Request request);

    Optional<Request> findById(UUID id);

    /**
     * Reads the request and locks its row without waiting ({@code FOR UPDATE NOWAIT}, 4.1); joins the caller transaction.
     *
     * @throws com.sb24.asanzeman.request.domain.model.RequestBusyException another transaction holds the row
     */
    Optional<Request> findByIdForUpdate(UUID id);

    /** Sets the status of a request whose row this transaction holds. */
    void updateStatus(UUID id, RequestStatus status, Instant at);

    /** The ids of issuance requests still at the agency step that were registered at or before {@code before}, oldest first. */
    List<UUID> incompleteSince(Instant before);

    /** @return true when this agency registered a request for this customer at or after {@code since} (D47) */
    boolean existsSince(UUID agencyId, String customerCode, Instant since);
}
