package com.sb24.asanzeman.request.domain.service;

import com.sb24.asanzeman.request.domain.model.CollateralDeposit;
import com.sb24.asanzeman.request.domain.model.CustomerDeposit;
import com.sb24.asanzeman.request.domain.model.DepositRules;
import com.sb24.asanzeman.request.domain.model.PriceClass;
import com.sb24.asanzeman.shared.model.Rials;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The deposits a customer may offer (4.3): held by the customer alone, as owner who also signs, a natural person's,
 * open and not blocked, in rials, and of a type the bank prices. The request to Yaghut already asks for these, and
 * the answer is checked again here (the plan: "filtered in the request and again in the backend"). The balance is
 * rounded <em>down</em> to the whole rial, so a customer is never offered more than the deposit holds.
 */
public final class DepositEligibility {

    static final String OPEN = "OPEN";
    static final String ACTUAL = "ACTUAL";
    static final String OWNER_WHO_SIGNS = "OWNER_OF_DEPOSIT_AND_SIGNATURE";

    private final DepositRules rules;

    public DepositEligibility(DepositRules rules) {
        this.rules = rules;
    }

    public List<CollateralDeposit> eligible(List<CustomerDeposit> deposits) {
        return deposits.stream().map(this::eligible).flatMap(Optional::stream).toList();
    }

    Optional<CollateralDeposit> eligible(CustomerDeposit d) {
        boolean allowed = d.number() != null && !d.number().isBlank()
                && OPEN.equals(d.status()) && ACTUAL.equals(d.personality()) && OWNER_WHO_SIGNS.equals(d.signature())
                && d.owner() != null && rules.individualOwners().contains(d.owner())
                && rules.rialCurrency().equals(d.currency()) && d.availableBalance() != null;
        PriceClass priceClass = allowed ? priceClass(d) : null;
        if (priceClass == null) {
            return Optional.empty();
        }
        long whole = d.availableBalance().setScale(0, RoundingMode.DOWN).longValueExact();
        return whole < 1 ? Optional.empty() : Optional.of(new CollateralDeposit(d.number(), priceClass,
                Rials.of(whole)));
    }

    private PriceClass priceClass(CustomerDeposit d) {
        PriceClass byType = d.typeCode() == null ? null : rules.typeCodes().get(d.typeCode());
        return byType != null ? byType : d.group() == null ? null : rules.groups().get(d.group());
    }
}
