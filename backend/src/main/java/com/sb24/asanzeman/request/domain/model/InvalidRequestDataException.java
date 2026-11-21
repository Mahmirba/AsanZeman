package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** A registration field breaks an SRS rule (D52): 400. The English detail names the field, for logs and support. */
public class InvalidRequestDataException extends DomainException {

    public static final String CODE = "request.invalid-data";

    public InvalidRequestDataException(String detail) {
        super(CODE, Kind.BAD_REQUEST, detail);
    }
}
