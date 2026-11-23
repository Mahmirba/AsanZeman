package com.sb24.asanzeman.request.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.CollateralDeposit;
import com.sb24.asanzeman.request.domain.model.CustomerDeposit;
import com.sb24.asanzeman.request.domain.model.DepositRules;
import com.sb24.asanzeman.request.domain.model.PriceClass;
import com.sb24.asanzeman.shared.model.Rials;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Which of the customer's deposits may be offered, and what each is priced as (4.3). */
class DepositEligibilityTest {

    private static final DepositRules RULES = new DepositRules("IRR", Set.of("INDIVIDUAL"),
            Map.of("SHORT_ACCOUNT", PriceClass.CHEAP, "PASANDAZ", PriceClass.CHEAP, "JARI_ACCOUNT", PriceClass.CHEAP,
                    "LONG_ACCOUNT", PriceClass.EXPENSIVE, "SPECIAL_SHORT_ACCOUNT", PriceClass.EXPENSIVE),
            Map.of(77L, PriceClass.CHEAP));

    private final DepositEligibility eligibility = new DepositEligibility(RULES);

    /** A deposit that passes every rule: change one thing to see which rule refuses it. */
    private static CustomerDeposit deposit() {
        return new CustomerDeposit("D1", "SHORT_ACCOUNT", 1L, "INDIVIDUAL", "OWNER_OF_DEPOSIT_AND_SIGNATURE", "OPEN",
                "IRR", "ACTUAL", new BigDecimal("1500000000"));
    }

    private List<CollateralDeposit> offer(CustomerDeposit d) {
        return eligibility.eligible(List.of(d));
    }

    @Test
    void anIndividualOpenRialDepositOfAnOwnerWhoSignsIsOffered() {
        assertThat(offer(deposit())).containsExactly(
                new CollateralDeposit("D1", PriceClass.CHEAP, Rials.of(1_500_000_000L)));
    }

    @ParameterizedTest
    @CsvSource({"SHORT_ACCOUNT,CHEAP", "PASANDAZ,CHEAP", "JARI_ACCOUNT,CHEAP", "LONG_ACCOUNT,EXPENSIVE",
            "SPECIAL_SHORT_ACCOUNT,EXPENSIVE"})
    void theGroupDecidesThePriceClass(String group, PriceClass expected) {
        var d = deposit();

        assertThat(offer(new CustomerDeposit(d.number(), group, null, d.owner(), d.signature(), d.status(),
                d.currency(), d.personality(), d.availableBalance())).get(0).priceClass()).isEqualTo(expected);
    }

    @Test
    void aTypeCodeDecidesOnItsOwnAndBeatsTheGroup() {
        var d = deposit(); // a long-term group deposit whose code says it is a cheap Win deposit
        var win = new CustomerDeposit("W1", "LONG_ACCOUNT", 77L, d.owner(), d.signature(), d.status(), d.currency(),
                d.personality(), d.availableBalance());

        assertThat(offer(win).get(0).priceClass()).isEqualTo(PriceClass.CHEAP);
    }

    @Test
    void aTypeCodeNobodyKnowsFallsBackToTheGroup() {
        var d = deposit();
        var other = new CustomerDeposit("D2", "LONG_ACCOUNT", 5L, d.owner(), d.signature(), d.status(), d.currency(),
                d.personality(), d.availableBalance());

        assertThat(offer(other).get(0).priceClass()).isEqualTo(PriceClass.EXPENSIVE);
    }

    @Test
    void aGroupTheBankDoesNotPriceIsNotOffered() {
        var d = deposit();
        for (String group : new String[] {"ANDOKHTE_ACCOUNT", "EDARE_SHODE", "OTHERS", "SPECIAL_LONG_ACCOUNT", null}) {
            assertThat(offer(new CustomerDeposit(d.number(), group, null, d.owner(), d.signature(), d.status(),
                    d.currency(), d.personality(), d.availableBalance()))).isEmpty();
        }
    }

    @Test
    void aJointDepositIsNotOffered() {
        var d = deposit();

        assertThat(offer(new CustomerDeposit(d.number(), d.group(), d.typeCode(), "JOINT", d.signature(), d.status(),
                d.currency(), d.personality(), d.availableBalance()))).isEmpty();
        assertThat(offer(new CustomerDeposit(d.number(), d.group(), d.typeCode(), null, d.signature(), d.status(),
                d.currency(), d.personality(), d.availableBalance()))).isEmpty();
    }

    @Test
    void anOwnerWhoDoesNotSignIsNotOffered() {
        var d = deposit();

        for (String signature : new String[] {"DEPOSIT_OWNER", "SIGNATURE_OWNER", "BROKER", null}) {
            assertThat(offer(new CustomerDeposit(d.number(), d.group(), d.typeCode(), d.owner(), signature,
                    d.status(), d.currency(), d.personality(), d.availableBalance()))).isEmpty();
        }
    }

    @Test
    void aDepositThatIsNotOpenIsNotOffered() {
        var d = deposit();

        for (String status : new String[] {"CLOSE", "NEGATIVE_BLOCK", "POSITIVE_BLOCK", "BLOCK", "RESTING", "OLD",
                "OPENING", null}) {
            assertThat(offer(new CustomerDeposit(d.number(), d.group(), d.typeCode(), d.owner(), d.signature(),
                    status, d.currency(), d.personality(), d.availableBalance()))).isEmpty();
        }
    }

    @Test
    void onlyANaturalPersonsRialDepositIsOffered() {
        var d = deposit();

        for (String personality : new String[] {"LEGAL", "SPECIAL", null}) {
            assertThat(offer(new CustomerDeposit(d.number(), d.group(), d.typeCode(), d.owner(), d.signature(),
                    d.status(), d.currency(), personality, d.availableBalance()))).isEmpty();
        }
        for (String currency : new String[] {"USD", "EUR", null}) {
            assertThat(offer(new CustomerDeposit(d.number(), d.group(), d.typeCode(), d.owner(), d.signature(),
                    d.status(), currency, d.personality(), d.availableBalance()))).isEmpty();
        }
    }

    @Test
    void aDepositWithoutANumberOrWithNothingToWithdrawIsNotOffered() {
        var d = deposit();

        for (String number : new String[] {null, " "}) {
            assertThat(offer(new CustomerDeposit(number, d.group(), d.typeCode(), d.owner(), d.signature(),
                    d.status(), d.currency(), d.personality(), d.availableBalance()))).isEmpty();
        }
        for (BigDecimal balance : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-5"),
                new BigDecimal("0.4")}) {
            assertThat(offer(new CustomerDeposit(d.number(), d.group(), d.typeCode(), d.owner(), d.signature(),
                    d.status(), d.currency(), d.personality(), balance))).isEmpty();
        }
    }

    @Test
    void theBalanceIsRoundedDownSoACustomerIsNeverOfferedMoreThanTheDepositHolds() {
        var d = deposit();

        assertThat(offer(new CustomerDeposit(d.number(), d.group(), d.typeCode(), d.owner(), d.signature(),
                d.status(), d.currency(), d.personality(), new BigDecimal("1500000000.99"))).get(0).availableBalance())
                .isEqualTo(Rials.of(1_500_000_000L));
    }

    @Test
    void severalDepositsKeepTheirOrderAndTheIneligibleOnesDropOut() {
        var good = deposit();
        var joint = new CustomerDeposit("D9", good.group(), good.typeCode(), "JOINT", good.signature(), good.status(),
                good.currency(), good.personality(), good.availableBalance());
        var second = new CustomerDeposit("D2", "LONG_ACCOUNT", 3L, good.owner(), good.signature(), good.status(),
                good.currency(), good.personality(), new BigDecimal("2000000000"));

        assertThat(eligibility.eligible(List.of(good, joint, second))).extracting(CollateralDeposit::number)
                .containsExactly("D1", "D2");
    }

    @Test
    void theRulesKeepTheirOwnCopyOfTheirSets() {
        var owners = new java.util.HashSet<>(Set.of("INDIVIDUAL"));
        var rules = new DepositRules("IRR", owners, Map.of(), Map.of());
        owners.clear();

        assertThat(rules.individualOwners()).containsExactly("INDIVIDUAL");
    }
}
