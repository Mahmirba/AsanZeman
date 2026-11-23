package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The collateral choice breaks a rule of the SRS (a share out of range, an expensive deposit for cash, …): 400. */
public class InvalidCollateralException extends DomainException {

    public static final String CODE = "request.collateral-invalid";

    private final String reason;

    public InvalidCollateralException(String reason) {
        super(CODE, Kind.BAD_REQUEST);
        this.reason = reason;
    }

    /** What is wrong, for the log; the customer sees the generic Persian text. */
    public String reason() {
        return reason;
    }

    @Override
    public String getMessage() {
        return CODE + ": " + reason;
    }
}
