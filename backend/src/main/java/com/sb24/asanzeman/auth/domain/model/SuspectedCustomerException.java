package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** Yaghut flagged the user as a suspected customer: 403 «امکان ارائه خدمت به شما از این مسیر وجود ندارد. لطفاً به شعبه مراجعه کنید.» (5.4) */
public class SuspectedCustomerException extends DomainException {

    public static final String CODE = "auth.yaghut.suspected-customer";

    public SuspectedCustomerException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
