package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.Rials;

/**
 * A deposit the customer may offer as collateral (4.3): it has already passed the eligibility rules. The balance is the
 * withdrawable balance ({@code availableBalance}), read once when the customer chooses and never saved (D16).
 */
public record CollateralDeposit(String number, PriceClass priceClass, Rials availableBalance) {

    public CollateralDeposit {
        if (number == null || number.isBlank() || priceClass == null || availableBalance == null) {
            throw new IllegalArgumentException("a collateral deposit needs a number, a price class and a balance");
        }
    }
}
