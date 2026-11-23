package com.sb24.asanzeman.request.domain.model;

import java.util.List;

/** One request as the branch reviews it: its row, the companions, and the collateral the customer chose (4.3). */
public record BranchRequestDetails(BranchRequestRow row, List<Companion> companions,
        List<CollateralAllocation> collateral) {

    public BranchRequestDetails {
        companions = List.copyOf(companions);
        collateral = List.copyOf(collateral);
    }
}
