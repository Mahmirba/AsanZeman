package com.sb24.asanzeman.request.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.request.domain.model.CollateralAllocation;
import com.sb24.asanzeman.request.domain.model.CollateralDeposit;
import com.sb24.asanzeman.request.domain.model.CollateralKind;
import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import com.sb24.asanzeman.request.domain.model.CollateralPolicy;
import com.sb24.asanzeman.request.domain.model.InsufficientBalanceException;
import com.sb24.asanzeman.request.domain.model.InvalidCollateralException;
import com.sb24.asanzeman.request.domain.model.PriceClass;
import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.Rials;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The SRS worked examples of 4.3 with a guarantee of 1,000,000,000 rials, and the rules around them (D14, D15, D18). */
class CollateralCalculatorTest {

    private static final Rials A = Rials.of(1_000_000_000L);
    private static final CollateralPolicy POLICY = new CollateralPolicy(20, 10, new BigDecimal("1.00"),
            new BigDecimal("1.05"), 95);

    private final CollateralCalculator calculator = new CollateralCalculator(POLICY);

    private static CollateralDeposit cheap(String number, long balance) {
        return new CollateralDeposit(number, PriceClass.CHEAP, Rials.of(balance));
    }

    private static CollateralDeposit expensive(String number, long balance) {
        return new CollateralDeposit(number, PriceClass.EXPENSIVE, Rials.of(balance));
    }

    private static List<CollateralDeposit> none() {
        return List.of();
    }

    // ---- the worked examples --------------------------------------------------------------------------------

    @Test
    void allCashIsOneBillionAndNeedsNoPledge() {
        var plan = calculator.plan(A, 100, List.of(cheap("D1", 2_000_000_000L)), none());

        assertThat(plan.cash()).isEqualTo(Rials.of(1_000_000_000L));
        assertThat(plan.pledgeBase()).isEqualTo(Rials.ZERO);
        assertThat(plan.totalCollateral()).isEqualTo(Rials.of(1_000_000_000L));
        assertThat(plan.allocations()).singleElement().satisfies(a -> {
            assertThat(a.kind()).isEqualTo(CollateralKind.CASH);
            assertThat(a.sharePercent()).isEqualTo(100);
            assertThat(a.allocated()).isEqualTo(Rials.of(1_000_000_000L));
        });
    }

    @Test
    void twentyPercentCashAndEightyPercentCheapPledgeTotalOneBillion() {
        var plan = calculator.plan(A, 20, List.of(cheap("D1", 300_000_000L)), List.of(cheap("D2", 900_000_000L)));

        assertThat(plan.cash()).isEqualTo(Rials.of(200_000_000L));
        assertThat(plan.pledgeBase()).isEqualTo(Rials.of(800_000_000L));
        assertThat(plan.totalCollateral()).isEqualTo(Rials.of(1_000_000_000L));
        assertThat(plan.allocations()).extracting(CollateralAllocation::kind, CollateralAllocation::sharePercent)
                .containsExactly(Tuple.tuple(CollateralKind.CASH, 20),
                        Tuple.tuple(CollateralKind.PLEDGE, 80));
    }

    @Test
    void twentyPercentCashAndEightyPercentExpensivePledgeTotalOnePointZeroFourBillion() {
        var plan = calculator.plan(A, 20, List.of(cheap("D1", 200_000_000L)),
                List.of(expensive("D2", 884_210_527L)));

        assertThat(plan.cash()).isEqualTo(Rials.of(200_000_000L));
        assertThat(plan.totalCollateral()).isEqualTo(Rials.of(1_040_000_000L));
        var pledge = plan.allocations().get(1);
        assertThat(pledge.allocated()).isEqualTo(Rials.of(800_000_000L));
        assertThat(pledge.collateral()).isEqualTo(Rials.of(840_000_000L));
        assertThat(pledge.factor()).isEqualByComparingTo("1.05");
    }

    @Test
    void theExpensiveDepositNeedsAtLeast884210527RialsAndOneRialLessIsNotEnough() {
        // 840,000,000 may be at most 95 % of the balance: 840,000,000 / 0.95 = 884,210,526.3 rounded up
        assertThatThrownBy(() -> calculator.plan(A, 20, List.of(cheap("D1", 200_000_000L)),
                List.of(expensive("D2", 884_210_526L)))).isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void fiftyPercentCashAndFiftyPercentExpensivePledgeTotalOnePointZeroTwoFiveBillion() {
        var plan = calculator.plan(A, 50, List.of(cheap("D1", 500_000_000L)),
                List.of(expensive("D2", 1_000_000_000L)));

        assertThat(plan.cash()).isEqualTo(Rials.of(500_000_000L));
        assertThat(plan.totalCollateral()).isEqualTo(Rials.of(1_025_000_000L));
        assertThat(plan.allocations().get(1).collateral()).isEqualTo(Rials.of(525_000_000L));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 15, 19, 25, 101, 110})
    void aCashShareBelowTwentyOrOffTheStepsOfTenIsRefused(int percent) {
        assertThatThrownBy(() -> calculator.plan(A, percent, List.of(cheap("D1", 2_000_000_000L)), none()))
                .isInstanceOf(InvalidCollateralException.class).hasMessageContaining(percent + "%");
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 30, 40, 50, 60, 70, 80, 90, 100})
    void everyAllowedCashShareIsAccepted(int percent) {
        var plan = calculator.plan(A, percent, List.of(cheap("D1", 2_000_000_000L)),
                List.of(cheap("D2", 2_000_000_000L)));

        assertThat(plan.cash().value()).isEqualTo(10_000_000L * percent);
        assertThat(plan.totalCollateral()).isEqualTo(A); // all cheap: nothing extra to hold
    }

    // ---- filling the deposits -------------------------------------------------------------------------------

    @Test
    void cheapDepositsAreFilledBeforeExpensiveOnesWhateverTheOrderSelected() {
        var plan = calculator.plan(A, 20, List.of(cheap("D1", 200_000_000L)),
                List.of(expensive("E1", 2_000_000_000L), cheap("C1", 300_000_000L)));

        // 800M base: 300M on the cheap deposit (x1.00), the other 500M on the expensive one (x1.05 = 525M)
        assertThat(plan.allocations()).extracting(CollateralAllocation::depositNumber, a -> a.allocated().value(),
                a -> a.collateral().value()).containsExactly(
                        Tuple.tuple("D1", 200_000_000L, 200_000_000L),
                        Tuple.tuple("C1", 300_000_000L, 300_000_000L),
                        Tuple.tuple("E1", 500_000_000L, 525_000_000L));
        assertThat(plan.totalCollateral()).isEqualTo(Rials.of(1_025_000_000L));
    }

    @Test
    void withinAGroupDepositsAreFilledInTheOrderSelected() {
        var plan = calculator.plan(A, 100, List.of(cheap("B", 600_000_000L), cheap("A", 600_000_000L)), none());

        assertThat(plan.allocations()).extracting(CollateralAllocation::depositNumber,
                a -> a.allocated().value()).containsExactly(Tuple.tuple("B", 600_000_000L),
                        Tuple.tuple("A", 400_000_000L));
    }

    @Test
    void severalExpensiveDepositsEachCappedAtNinetyFivePercent() {
        // each can take blocked <= 95 % of 500M = 475M, a base of 452,380,952 (475,000,000 / 1.05 rounded down)
        var plan = calculator.plan(A, 20, List.of(cheap("D1", 200_000_000L)),
                List.of(expensive("E1", 500_000_000L), expensive("E2", 500_000_000L)));

        var e1 = plan.allocations().get(1);
        var e2 = plan.allocations().get(2);
        assertThat(e1.allocated().value()).isEqualTo(452_380_952L);
        assertThat(e1.collateral().value()).isLessThanOrEqualTo(475_000_000L);
        assertThat(e1.allocated().plus(e2.allocated())).isEqualTo(Rials.of(800_000_000L));
        assertThat(e2.collateral().value()).isLessThanOrEqualTo(475_000_000L);
    }

    @Test
    void aDepositUsedForBothCashAndPledgeMustHoldBothAtOnce() {
        // 600M balance: 200M is taken as cash, so only 400M is left to block
        var plan = calculator.plan(A, 20, List.of(cheap("D1", 600_000_000L)),
                List.of(cheap("D1", 600_000_000L), cheap("D2", 400_000_000L)));

        assertThat(plan.allocations()).extracting(CollateralAllocation::depositNumber, CollateralAllocation::kind,
                a -> a.allocated().value()).containsExactly(
                        Tuple.tuple("D1", CollateralKind.CASH, 200_000_000L),
                        Tuple.tuple("D1", CollateralKind.PLEDGE, 400_000_000L),
                        Tuple.tuple("D2", CollateralKind.PLEDGE, 400_000_000L));
    }

    @Test
    void aDepositWithNothingLeftOrNothingNeededGetsNoAllocation() {
        var plan = calculator.plan(A, 100, List.of(cheap("EMPTY", 0), cheap("D1", 1_000_000_000L),
                cheap("UNUSED", 5_000_000_000L)), none());

        assertThat(plan.allocations()).extracting(CollateralAllocation::depositNumber).containsExactly("D1");
    }

    @Test
    void whenCashCoversEverythingAPledgeDepositIsNotUsed() {
        // the guarantee is 1 rial: 20 % cash rounds up to the whole rial, so nothing is left to pledge
        var plan = calculator.plan(Rials.of(1), 20, List.of(cheap("D1", 10)), List.of(cheap("D2", 10)));

        assertThat(plan.allocations()).extracting(CollateralAllocation::depositNumber).containsExactly("D1");
        assertThat(plan.pledgeBase()).isEqualTo(Rials.ZERO);
    }

    // ---- rounding (D18) -------------------------------------------------------------------------------------

    @Test
    void everythingIsRoundedUpToTheWholeRial() {
        // 30 % of 1,000,000,003 = 300,000,000.9 -> 300,000,001; base 700,000,002; x1.05 = 735,000,002.1 -> 735,000,003
        var plan = calculator.plan(Rials.of(1_000_000_003L), 30, List.of(cheap("D1", 400_000_000L)),
                List.of(expensive("E1", 2_000_000_000L)));

        assertThat(plan.cash()).isEqualTo(Rials.of(300_000_001L));
        assertThat(plan.pledgeBase()).isEqualTo(Rials.of(700_000_002L));
        assertThat(plan.allocations().get(1).collateral()).isEqualTo(Rials.of(735_000_003L));
        assertThat(plan.totalCollateral()).isEqualTo(Rials.of(1_035_000_004L));
    }

    // ---- what is refused ------------------------------------------------------------------------------------

    @Test
    void anExpensiveDepositCannotBeUsedForCash() {
        assertThatThrownBy(() -> calculator.plan(A, 100, List.of(expensive("E1", 5_000_000_000L)), none()))
                .isInstanceOf(InvalidCollateralException.class).hasMessageContaining("expensive");
    }

    @Test
    void aDepositChosenTwiceInTheSameListIsRefused() {
        assertThatThrownBy(() -> calculator.plan(A, 100, List.of(cheap("D1", 1), cheap("D1", 1)), none()))
                .isInstanceOf(InvalidCollateralException.class).hasMessageContaining("D1");
        assertThatThrownBy(() -> calculator.plan(A, 20, List.of(cheap("D0", 200_000_000L)),
                List.of(cheap("D1", 1), cheap("D1", 1)))).isInstanceOf(InvalidCollateralException.class);
    }

    @Test
    void aGuaranteeOfNothingIsRefused() {
        assertThatThrownBy(() -> calculator.plan(Rials.ZERO, 100, List.of(cheap("D1", 1)), none()))
                .isInstanceOf(InvalidCollateralException.class);
    }

    @Test
    void aPledgeNeedsADepositToBlockIn() {
        assertThatThrownBy(() -> calculator.plan(A, 20, List.of(cheap("D1", 200_000_000L)), none()))
                .isInstanceOf(InvalidCollateralException.class).hasMessageContaining("pledge");
    }

    @Test
    void notEnoughCashIsInsufficientBalance() {
        assertThatThrownBy(() -> calculator.plan(A, 100, List.of(cheap("D1", 999_999_999L)), none()))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void notEnoughToPledgeIsInsufficientBalance() {
        assertThatThrownBy(() -> calculator.plan(A, 20, List.of(cheap("D1", 200_000_000L)),
                List.of(cheap("D2", 799_999_999L)))).isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void aDepositUsedForCashHasLessLeftForThePledge() {
        assertThatThrownBy(() -> calculator.plan(A, 20, List.of(cheap("D1", 700_000_000L)),
                List.of(cheap("D1", 700_000_000L)))).isInstanceOf(InsufficientBalanceException.class);
    }

    // ---- the small types ------------------------------------------------------------------------------------

    @Test
    void theRefusalsHaveTheRightStatusKinds() {
        assertThat(new InvalidCollateralException("x").kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
        assertThat(new InvalidCollateralException("x").reason()).isEqualTo("x");
        assertThat(new InsufficientBalanceException().kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
    }

    @Test
    void aDepositAndAPolicyMustBeComplete() {
        assertThatThrownBy(() -> new CollateralDeposit(" ", PriceClass.CHEAP, Rials.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollateralDeposit(null, PriceClass.CHEAP, Rials.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollateralDeposit("D", null, Rials.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollateralDeposit("D", PriceClass.CHEAP, null))
                .isInstanceOf(IllegalArgumentException.class);
        for (int[] bad : new int[][] {{0, 10, 95}, {101, 10, 95}, {20, 0, 95}, {20, 101, 95}, {20, 10, 0},
                {20, 10, 101}}) {
            assertThatThrownBy(() -> new CollateralPolicy(bad[0], bad[1], BigDecimal.ONE, BigDecimal.ONE, bad[2]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new CollateralPolicy(20, 10, BigDecimal.ZERO, BigDecimal.ONE, 95))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollateralPolicy(20, 10, BigDecimal.ONE, new BigDecimal("-1"), 95))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePolicyKnowsTheFactorAndTheBalanceLimitOfEachPriceClass() {
        assertThat(POLICY.factor(PriceClass.CHEAP)).isEqualByComparingTo("1.00");
        assertThat(POLICY.factor(PriceClass.EXPENSIVE)).isEqualByComparingTo("1.05");
        assertThat(POLICY.maxBalancePercent(PriceClass.CHEAP)).isEqualTo(100);
        assertThat(POLICY.maxBalancePercent(PriceClass.EXPENSIVE)).isEqualTo(95);
    }

    @Test
    void aPlanKeepsItsOwnCopyOfTheAllocations() {
        var list = new ArrayList<CollateralAllocation>();
        var plan = new CollateralPlan(list, Rials.ZERO, Rials.ZERO, Rials.ZERO);
        list.add(null);

        assertThat(plan.allocations()).isEmpty();
    }
}
