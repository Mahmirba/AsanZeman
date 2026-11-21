package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** No request with this id for this agency (a request of another agency looks the same): 404 «درخواست یافت نشد.» */
public class RequestNotFoundException extends DomainException {

    public static final String CODE = "request.not-found";

    public RequestNotFoundException() {
        super(CODE, Kind.NOT_FOUND);
    }
}
