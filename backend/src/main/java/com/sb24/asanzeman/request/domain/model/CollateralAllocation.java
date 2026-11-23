package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.Rials;
import java.math.BigDecimal;

/**
 * What one deposit contributes (4.3): the kind, the share of the request it stands for, the factor, the amount taken
 * from or blocked in the deposit's share ({@code allocated}), and the collateral that amount is worth ({@code collateral},
 * {@code allocated × factor}, rounded up). For cash the two are the same.
 */
public record CollateralAllocation(String depositNumber, CollateralKind kind, PriceClass priceClass,
        int sharePercent, BigDecimal factor, Rials allocated, Rials collateral) {
}
