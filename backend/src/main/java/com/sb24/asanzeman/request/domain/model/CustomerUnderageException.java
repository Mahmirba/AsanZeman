package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The customer is under 18 (SRS UC-AZS-8, alternative flow 5): 400 «کاربر گرامی! باتوجه به اینکه سن مشتری کمتر از 18 سال است، امکان ثبت درخواست فراهم نیست.» */
public class CustomerUnderageException extends DomainException {

    public static final String CODE = "request.customer-underage";

    public CustomerUnderageException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
