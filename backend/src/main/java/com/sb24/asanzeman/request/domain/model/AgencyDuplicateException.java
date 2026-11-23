package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** Another agency already has this customer number or code: 409. */
public class AgencyDuplicateException extends DomainException {

    public static final String CODE = "admin.agency-duplicate";

    public AgencyDuplicateException() {
        super(CODE, Kind.CONFLICT);
    }
}
