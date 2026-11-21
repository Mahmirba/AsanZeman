package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** Yaghut does not know this national code (SRS UC-AZS-8, alternative flow 4): 400 «اطلاعات وارد شده مربوط به مشتری بانک نیست.» */
public class NotBankCustomerException extends DomainException {

    public static final String CODE = "request.customer-not-found";

    public NotBankCustomerException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
