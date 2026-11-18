package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** AAA accepted the user but none of their roles maps to Asan Zeman: 403 «شما مجوز دسترسی به سامانه آسان ضمان را ندارید.» */
public class BankAccessDeniedException extends DomainException {

    public static final String CODE = "auth.bank.no-access";

    public BankAccessDeniedException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
