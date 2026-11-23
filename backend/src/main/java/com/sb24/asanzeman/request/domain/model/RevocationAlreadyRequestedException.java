package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** A revocation request is already open for this guarantee, so a second one is refused (4.2): 409. */
public class RevocationAlreadyRequestedException extends DomainException {

    public static final String CODE = "request.revocation-duplicate";

    public RevocationAlreadyRequestedException() {
        super(CODE, Kind.CONFLICT);
    }
}
