package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** Yaghut refused the user name or password: 401 «نام کاربری یا رمز عبور اشتباه است.» (5.4) */
public class InvalidYaghutCredentialsException extends DomainException {

    public static final String CODE = "auth.yaghut.invalid-credentials";

    public InvalidYaghutCredentialsException() {
        super(CODE, Kind.UNAUTHORIZED);
    }
}
