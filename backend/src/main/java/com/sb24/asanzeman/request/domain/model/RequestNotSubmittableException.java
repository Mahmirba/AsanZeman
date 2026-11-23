package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/**
 * The request cannot be submitted yet (5.1, 4.3): it is not waiting at the agency's step, its inquiries have not all
 * passed, or no collateral was chosen. 409.
 */
public class RequestNotSubmittableException extends DomainException {

    public static final String CODE = "request.not-submittable";

    private final String reason;

    public RequestNotSubmittableException(String reason) {
        super(CODE, Kind.CONFLICT);
        this.reason = reason;
    }

    /** What is missing, for the log. */
    public String reason() {
        return reason;
    }

    @Override
    public String getMessage() {
        return CODE + ": " + reason;
    }
}
