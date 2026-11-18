package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/**
 * The refresh token is unknown, expired, revoked or already used: 401 «نشست شما منقضی شده است. لطفاً دوباره وارد شوید.»
 * The same answer is given for every cause, so a caller learns nothing about which tokens exist.
 */
public class InvalidSessionException extends DomainException {

    public static final String CODE = "auth.session-invalid";

    public InvalidSessionException() {
        super(CODE, Kind.UNAUTHORIZED);
    }
}
