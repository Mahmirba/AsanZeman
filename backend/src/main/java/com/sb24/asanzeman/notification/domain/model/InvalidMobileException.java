package com.sb24.asanzeman.notification.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** A mobile number that is not {@code 09} followed by nine digits: 400, nothing is queued. */
public class InvalidMobileException extends DomainException {

    public static final String CODE = "notification.invalid-mobile";

    public InvalidMobileException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
