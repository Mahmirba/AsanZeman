package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/**
 * The branch action does not fit where the request is (4.1): for example approving a request nobody has taken, or
 * entering a guarantee number before the request was approved. 409.
 */
public class RequestNotReviewableException extends DomainException {

    public static final String CODE = "request.not-reviewable";

    private final String reason;

    public RequestNotReviewableException(String reason) {
        super(CODE, Kind.CONFLICT);
        this.reason = reason;
    }

    /** What does not fit, for the log. */
    public String reason() {
        return reason;
    }

    @Override
    public String getMessage() {
        return CODE + ": " + reason;
    }
}
