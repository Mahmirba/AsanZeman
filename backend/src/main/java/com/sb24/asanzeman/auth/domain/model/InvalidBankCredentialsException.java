package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** AAA refused the user name or password: 401 «نام کاربری یا رمز عبور اشتباه است.» (6.2) */
public class InvalidBankCredentialsException extends DomainException {

    public static final String CODE = "auth.bank.invalid-credentials";

    public InvalidBankCredentialsException() {
        super(CODE, Kind.UNAUTHORIZED);
    }
}
