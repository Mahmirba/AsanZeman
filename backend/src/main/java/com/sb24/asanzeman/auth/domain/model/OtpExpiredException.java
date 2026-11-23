package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The code is too old, already used, or unknown: «کد تأیید منقضی شده است. لطفاً کد جدید دریافت کنید.» (6.3): 400. */
public class OtpExpiredException extends DomainException {

    public static final String CODE = "auth.otp.expired";

    public OtpExpiredException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
