package com.sb24.asanzeman.request.domain.port.in;

import java.util.List;

/**
 * The customer's collateral choice (SRS steps 11 to 13): the cash share, and the deposit numbers for the cash and for
 * the pledge, in the order the customer selected them. A deposit may be in both lists.
 */
public record ChooseCollateralCommand(int cashPercent, List<String> cashDeposits, List<String> pledgeDeposits) {

    public ChooseCollateralCommand {
        cashDeposits = cashDeposits == null ? List.of() : List.copyOf(cashDeposits);
        pledgeDeposits = pledgeDeposits == null ? List.of() : List.copyOf(pledgeDeposits);
    }
}
