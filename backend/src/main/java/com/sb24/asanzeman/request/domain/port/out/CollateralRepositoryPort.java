package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import java.time.Instant;
import java.util.UUID;

/** The {@code collateral} table: one row per deposit allocation of a request's chosen collateral (4.3). */
public interface CollateralRepositoryPort {

    /** Saves the plan as the request's collateral, replacing any earlier choice; joins the caller's transaction. */
    void replace(UUID requestId, CollateralPlan plan, Instant at);

    /** @return whether the request has chosen its collateral */
    boolean exists(UUID requestId);

    /** The saved allocations of a request, cash first, in the order they were chosen. */
    java.util.List<com.sb24.asanzeman.request.domain.model.CollateralAllocation> find(UUID requestId);
}
