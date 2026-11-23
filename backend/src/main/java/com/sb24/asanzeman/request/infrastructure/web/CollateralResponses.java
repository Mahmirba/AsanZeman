package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.CollateralAllocation;
import com.sb24.asanzeman.request.domain.model.CollateralDeposit;
import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import com.sb24.asanzeman.request.domain.model.PriceClass;
import java.math.BigDecimal;
import java.util.List;

/** What the customer panel gets back for the collateral steps (4.3). Amounts are whole rials. */
final class CollateralResponses {

    private CollateralResponses() {
    }

    /** A deposit that can be offered. {@code allowedFor}: cash and pledge for a cheap deposit, a pledge only for an expensive one. */
    record Deposit(String number, String priceClass, long availableBalance, List<String> allowedFor) {

        static Deposit from(CollateralDeposit d) {
            return new Deposit(d.number(), d.priceClass().name(), d.availableBalance().value(),
                    d.priceClass() == PriceClass.CHEAP ? List.of("CASH", "PLEDGE") : List.of("PLEDGE"));
        }
    }

    record Allocation(String depositNumber, String kind, String priceClass, int sharePercent, BigDecimal factor,
            long allocated, long collateral) {

        static Allocation from(CollateralAllocation a) {
            return new Allocation(a.depositNumber(), a.kind().name(), a.priceClass().name(), a.sharePercent(),
                    a.factor(), a.allocated().value(), a.collateral().value());
        }
    }

    /** The chosen collateral: the cash, what the pledge covers, and the total the bank will hold. */
    record Plan(long cash, long pledgeBase, long totalCollateral, List<Allocation> allocations) {

        static Plan from(CollateralPlan p) {
            return new Plan(p.cash().value(), p.pledgeBase().value(), p.totalCollateral().value(),
                    p.allocations().stream().map(Allocation::from).toList());
        }
    }

    record Submitted(String status, String statusLabel) {
    }
}
