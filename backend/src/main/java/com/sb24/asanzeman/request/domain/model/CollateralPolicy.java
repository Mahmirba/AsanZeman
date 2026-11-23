package com.sb24.asanzeman.request.domain.model;

import java.math.BigDecimal;

/**
 * The collateral rules that live in configuration (4.3, {@code azs.collateral.*}): the smallest cash share and the step
 * of every share, the pledge factors, and how much of an expensive deposit's balance may be blocked.
 */
public record CollateralPolicy(int cashMinPercent, int shareStepPercent, BigDecimal cheapFactor,
        BigDecimal expensiveFactor, int expensiveMaxBalancePercent) {

    public CollateralPolicy {
        if (cashMinPercent < 1 || cashMinPercent > 100 || shareStepPercent < 1 || shareStepPercent > 100
                || expensiveMaxBalancePercent < 1 || expensiveMaxBalancePercent > 100
                || cheapFactor.signum() <= 0 || expensiveFactor.signum() <= 0) {
            throw new IllegalArgumentException("the collateral policy has a value out of range");
        }
    }

    public BigDecimal factor(PriceClass priceClass) {
        return priceClass == PriceClass.CHEAP ? cheapFactor : expensiveFactor;
    }

    /** Only expensive deposits are limited: a cheap one may be blocked up to its whole balance. */
    public int maxBalancePercent(PriceClass priceClass) {
        return priceClass == PriceClass.CHEAP ? 100 : expensiveMaxBalancePercent;
    }
}
