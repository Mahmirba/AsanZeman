package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** Inquiries only run for an issuance request still waiting at the agency's step (5.1): 409. */
public class RequestNotInquirableException extends DomainException {

    public static final String CODE = "request.not-inquirable";

    public RequestNotInquirableException() {
        super(CODE, Kind.CONFLICT);
    }
}
