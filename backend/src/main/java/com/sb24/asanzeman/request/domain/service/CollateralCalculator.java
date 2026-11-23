package com.sb24.asanzeman.request.domain.service;

import com.sb24.asanzeman.request.domain.model.CollateralAllocation;
import com.sb24.asanzeman.request.domain.model.CollateralDeposit;
import com.sb24.asanzeman.request.domain.model.CollateralKind;
import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import com.sb24.asanzeman.request.domain.model.CollateralPolicy;
import com.sb24.asanzeman.request.domain.model.InsufficientBalanceException;
import com.sb24.asanzeman.request.domain.model.InvalidCollateralException;
import com.sb24.asanzeman.request.domain.model.PriceClass;
import com.sb24.asanzeman.shared.model.Rials;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The collateral of a guarantee (4.3, D14, D15, D18). With {@code A} the guarantee amount and {@code c} the cash share:
 *
 * <ul>
 *   <li>{@code cash = A × c}, taken from the cash deposits in the order the customer selected them; only cheap deposits
 *       may be used for cash;</li>
 *   <li>{@code pledge base = A − cash}, blocked in the pledge deposits: cheap deposits first (×1.00), then expensive
 *       ones (×1.05), each in the order selected. An expensive deposit may be blocked up to 95 % of its balance only;
 *   <li>a deposit used for both cash and a pledge must hold both: the pledge sees only what the cash left;</li>
 *   <li>everything is rounded <em>up</em> to the whole rial, so the bank is never under-collateralised.</li>
 * </ul>
 *
 * The balances are the ones read when the customer chose; nothing here saves them (D16). Pure Java, no framework.
 */
public final class CollateralCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CollateralPolicy policy;

    public CollateralCalculator(CollateralPolicy policy) {
        this.policy = policy;
    }

    /**
     * @param cashPercent  the cash share, from the policy's minimum to 100, in the policy's steps; the pledge share is
     *                     the rest
     * @throws InvalidCollateralException      a share out of range, a deposit chosen twice or an expensive one for cash
     * @throws InsufficientBalanceException    the chosen deposits do not hold enough
     */
    public CollateralPlan plan(Rials amount, int cashPercent, List<CollateralDeposit> cashDeposits,
            List<CollateralDeposit> pledgeDeposits) {
        if (amount.value() < 1) {
            throw new InvalidCollateralException("the guarantee amount must be positive");
        }
        if (cashPercent < policy.cashMinPercent() || cashPercent > 100 || cashPercent % policy.shareStepPercent() != 0) {
            throw new InvalidCollateralException("the cash share " + cashPercent + "% is not allowed");
        }
        requireDistinct(cashDeposits);
        requireDistinct(pledgeDeposits);
        if (cashDeposits.stream().anyMatch(d -> d.priceClass() == PriceClass.EXPENSIVE)) {
            throw new InvalidCollateralException("an expensive deposit cannot be used for cash");
        }

        List<CollateralAllocation> allocations = new ArrayList<>();
        Rials cash = Rials.roundUp(BigDecimal.valueOf(amount.value()).multiply(BigDecimal.valueOf(cashPercent))
                .divide(HUNDRED));
        Map<String, Long> cashTaken = new HashMap<>();
        long missing = cash.value();
        for (CollateralDeposit deposit : cashDeposits) {
            long take = Math.min(missing, deposit.availableBalance().value());
            if (take > 0) {
                allocations.add(new CollateralAllocation(deposit.number(), CollateralKind.CASH, deposit.priceClass(),
                        cashPercent, BigDecimal.ONE, Rials.of(take), Rials.of(take)));
                cashTaken.put(deposit.number(), take);
                missing -= take;
            }
        }
        if (missing > 0) {
            throw new InsufficientBalanceException();
        }

        Rials pledgeBase = amount.minus(cash);
        Rials total = cash;
        missing = pledgeBase.value();
        if (missing > 0 && pledgeDeposits.isEmpty()) {
            throw new InvalidCollateralException("a deposit is needed for the pledge");
        }
        for (CollateralDeposit deposit : cheapFirst(pledgeDeposits)) {
            BigDecimal factor = policy.factor(deposit.priceClass());
            long take = Math.min(missing, largestBase(deposit, cashTaken.getOrDefault(deposit.number(), 0L), factor));
            if (take > 0) {
                Rials collateral = Rials.roundUp(BigDecimal.valueOf(take).multiply(factor));
                allocations.add(new CollateralAllocation(deposit.number(), CollateralKind.PLEDGE,
                        deposit.priceClass(), 100 - cashPercent, factor, Rials.of(take), collateral));
                total = total.plus(collateral);
                missing -= take;
            }
        }
        if (missing > 0) {
            throw new InsufficientBalanceException();
        }
        return new CollateralPlan(allocations, cash, pledgeBase, total);
    }

    /**
     * The most of the pledge base a deposit can take: what is left after the cash, limited to the share of its balance
     * that may be blocked, and divided by the factor, because the blocked amount is {@code base × factor}. All rounded
     * down, so the rounded-up blocked amount never goes over the limit.
     */
    private long largestBase(CollateralDeposit deposit, long cashTaken, BigDecimal factor) {
        long free = deposit.availableBalance().value() - cashTaken;
        BigDecimal blockable = BigDecimal.valueOf(free).multiply(BigDecimal.valueOf(
                policy.maxBalancePercent(deposit.priceClass()))).divide(HUNDRED, 0, RoundingMode.DOWN);
        return blockable.divide(factor, 0, RoundingMode.DOWN).longValueExact();
    }

    private static List<CollateralDeposit> cheapFirst(List<CollateralDeposit> deposits) {
        return Stream.concat(deposits.stream().filter(d -> d.priceClass() == PriceClass.CHEAP),
                deposits.stream().filter(d -> d.priceClass() == PriceClass.EXPENSIVE)).toList();
    }

    private static void requireDistinct(List<CollateralDeposit> deposits) {
        var seen = new HashSet<String>();
        for (CollateralDeposit deposit : deposits) {
            if (!seen.add(deposit.number())) {
                throw new InvalidCollateralException("deposit " + deposit.number() + " is chosen twice");
            }
        }
    }
}
