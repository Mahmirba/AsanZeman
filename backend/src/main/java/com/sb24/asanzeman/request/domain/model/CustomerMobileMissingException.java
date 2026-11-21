package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The bank has no mobile number for the customer, so the completion SMS cannot be sent: 400 «شماره تلفن همراه مشتری در بانک ثبت نشده است.» The wording is ours. */
public class CustomerMobileMissingException extends DomainException {

    public static final String CODE = "request.customer-mobile-missing";

    public CustomerMobileMissingException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
