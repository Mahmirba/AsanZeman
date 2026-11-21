package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** Yaghut issued only a temporary session: 403 «لطفاً از طریق اینترنت‌بانک وارد شوید.» (5.4) */
public class TemporarySessionException extends DomainException {

    public static final String CODE = "auth.yaghut.temporary-session";

    public TemporarySessionException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
