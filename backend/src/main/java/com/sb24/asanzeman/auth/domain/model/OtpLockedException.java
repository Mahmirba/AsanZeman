package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** Too many wrong codes: the customer is locked out for a while (D34): 403. */
public class OtpLockedException extends DomainException {

    public static final String CODE = "auth.otp.locked";

    public OtpLockedException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
