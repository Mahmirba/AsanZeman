package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The user's principal is not active for the Yaghut service: 403 «دسترسی شما به این سرویس یاقوت فعال نیست. لطفاً با راهبر سامانه تماس بگیرید.» (5.4) */
public class YaghutAccessDeniedException extends DomainException {

    public static final String CODE = "auth.yaghut.access-denied";

    public YaghutAccessDeniedException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
