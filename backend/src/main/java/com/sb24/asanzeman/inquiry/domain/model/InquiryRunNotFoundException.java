package com.sb24.asanzeman.inquiry.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** No such run for this request: 404. */
public class InquiryRunNotFoundException extends DomainException {

    public static final String CODE = "inquiry.run-not-found";

    public InquiryRunNotFoundException() {
        super(CODE, Kind.NOT_FOUND);
    }
}
