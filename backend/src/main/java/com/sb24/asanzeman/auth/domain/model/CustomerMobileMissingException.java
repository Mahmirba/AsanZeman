package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The bank has no mobile number for the customer, so no SMS code can be sent (6.3): 403. */
public class CustomerMobileMissingException extends DomainException {

    public static final String CODE = "auth.customer.mobile-missing";

    public CustomerMobileMissingException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
