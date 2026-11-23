package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/**
 * The selected deposits do not hold enough for the cash or the pledge (SRS sub-flow 11): the request cannot be
 * submitted, and the customer picks deposits with enough balance. 400.
 */
public class InsufficientBalanceException extends DomainException {

    public static final String CODE = "request.collateral-insufficient";

    public InsufficientBalanceException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
