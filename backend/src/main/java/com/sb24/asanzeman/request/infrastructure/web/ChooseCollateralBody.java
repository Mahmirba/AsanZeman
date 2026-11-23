package com.sb24.asanzeman.request.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Body of {@code POST /api/customer/requests/{id}/collateral}: the cash share and the deposits, in the order selected. */
public record ChooseCollateralBody(@NotNull Integer cashPercent, List<String> cashDeposits,
        List<String> pledgeDeposits) {
}
