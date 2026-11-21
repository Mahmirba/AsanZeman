package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/**
 * The user's Yaghut session has ended or is gone: 401 «نشست شما در یاقوت منقضی شده است. لطفاً دوباره وارد شوید.» (5.4).
 * Agency and customer passwords are never stored, so the session cannot be renewed: the user must log in again.
 */
public class YaghutSessionExpiredException extends DomainException {

    public static final String CODE = "auth.yaghut.session-expired";

    public YaghutSessionExpiredException() {
        super(CODE, Kind.UNAUTHORIZED);
    }
}
