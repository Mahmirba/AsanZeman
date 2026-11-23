package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The agency or reason the admin asked for does not exist: 404. */
public class AdminItemNotFoundException extends DomainException {

    public static final String CODE = "admin.not-found";

    public AdminItemNotFoundException() {
        super(CODE, Kind.NOT_FOUND);
    }
}
