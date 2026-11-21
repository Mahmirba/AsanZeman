package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/**
 * The customer is not a natural person ({@code personalityType} is not {@code ACTUAL}, D21): 400
 * «امکان ثبت درخواست برای این مشتری وجود ندارد.» The wording is ours, the SRS has none for this case.
 */
public class CustomerNotActualException extends DomainException {

    public static final String CODE = "request.customer-not-actual";

    public CustomerNotActualException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
