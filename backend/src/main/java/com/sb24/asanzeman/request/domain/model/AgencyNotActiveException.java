package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The agency was deactivated after it logged in: 403 «آژانس شما غیرفعال است.» */
public class AgencyNotActiveException extends DomainException {

    public static final String CODE = "request.agency-not-active";

    public AgencyNotActiveException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
