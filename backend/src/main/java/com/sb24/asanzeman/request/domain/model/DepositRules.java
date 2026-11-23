package com.sb24.asanzeman.request.domain.model;

import java.util.Map;
import java.util.Set;

/**
 * Which deposits may be offered as collateral and how they are priced (4.3). The mapping lives in configuration so it
 * can change without a release ({@code azs.collateral.*}): the rial's currency code, the {@code owner} values that mean
 * "held by the customer alone", the price class of each Yaghut group, and the numeric type codes that decide a class on
 * their own (the Win deposits share a group with others). A type code wins over the group.
 */
public record DepositRules(String rialCurrency, Set<String> individualOwners, Map<String, PriceClass> groups,
        Map<Long, PriceClass> typeCodes) {

    public DepositRules {
        individualOwners = Set.copyOf(individualOwners);
        groups = Map.copyOf(groups);
        typeCodes = Map.copyOf(typeCodes);
    }
}
