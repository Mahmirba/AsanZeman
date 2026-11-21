package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import java.util.Optional;
import java.util.UUID;

/** The {@code agency} table, as registration needs it. */
public interface AgencyRegistryPort {

    /**
     * Reads the agency and locks its row ({@code SELECT … FOR UPDATE}) until the transaction ends, so two
     * registrations of one agency run one after the other (D47). Joins the caller's transaction.
     */
    Optional<AgencyInfo> lock(UUID agencyId);

    /** Reads the agency without locking it. */
    Optional<AgencyInfo> find(UUID agencyId);
}
