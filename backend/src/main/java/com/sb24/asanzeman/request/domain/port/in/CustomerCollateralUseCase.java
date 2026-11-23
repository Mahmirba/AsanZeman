package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.CollateralDeposit;
import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import java.util.List;
import java.util.UUID;

/**
 * The last steps of the customer's completion (4.3): look at the deposits that can be offered, choose the collateral,
 * and submit. All three need the inquiries to have passed, and all are for the customer's own request.
 */
public interface CustomerCollateralUseCase {

    /**
     * The deposits the customer may offer, with their balances as they are now.
     *
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotFoundException      not this customer's request
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotSubmittableException the inquiries have not passed
     */
    List<CollateralDeposit> deposits(String nationalCode, UUID sessionId, UUID requestId);

    /**
     * Calculates the collateral from the balances as they are now, and saves it as the request's choice (a new choice
     * replaces the old one).
     *
     * @throws com.sb24.asanzeman.request.domain.model.InvalidCollateralException   a rule of the SRS is broken
     * @throws com.sb24.asanzeman.request.domain.model.InsufficientBalanceException the deposits do not hold enough
     */
    CollateralPlan choose(String nationalCode, UUID sessionId, UUID requestId, ChooseCollateralCommand command);

    /**
     * Sends the request to the branch (status «در انتظار بررسی»).
     *
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotSubmittableException the inquiries have not passed, or no
     *         collateral was chosen, or the request is not at the agency's step
     */
    RequestStatus submit(String nationalCode, UUID requestId);
}
