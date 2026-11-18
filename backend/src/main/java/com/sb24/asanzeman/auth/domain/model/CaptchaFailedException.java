package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The captcha was missing or wrong: 400 «لطفاً کپچا را دوباره حل کنید.» (6.5). */
public class CaptchaFailedException extends DomainException {

    public static final String CODE = "auth.captcha-invalid";

    public CaptchaFailedException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
