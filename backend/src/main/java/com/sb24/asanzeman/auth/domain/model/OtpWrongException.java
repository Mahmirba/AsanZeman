package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The code is wrong: «کد تأیید وارد شده صحیح نیست.» (6.3): 400. */
public class OtpWrongException extends DomainException {

    public static final String CODE = "auth.otp.wrong";

    public OtpWrongException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
