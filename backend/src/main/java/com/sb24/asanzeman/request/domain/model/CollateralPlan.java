package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.Rials;
import java.util.List;

/**
 * The outcome of a collateral choice (4.3): every deposit's allocation, the cash, the base the pledge covers
 * ({@code guarantee amount − cash}), and the total collateral the bank holds ({@code cash + Σ pledge collateral}).
 */
public record CollateralPlan(List<CollateralAllocation> allocations, Rials cash, Rials pledgeBase,
        Rials totalCollateral) {

    public CollateralPlan {
        allocations = List.copyOf(allocations);
    }
}
