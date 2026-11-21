package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The user's Yaghut password has expired: 403 «رمز عبور شما منقضی شده است. لطفاً ابتدا رمز خود را در اینترنت‌بانک تغییر دهید.» (5.4) */
public class PasswordExpiredException extends DomainException {

    public static final String CODE = "auth.yaghut.password-expired";

    public PasswordExpiredException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
