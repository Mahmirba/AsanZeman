package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** Another request already carries this guarantee number, so it cannot be recorded again: 409. */
public class GuaranteeNumberTakenException extends DomainException {

    public static final String CODE = "request.guarantee-number-taken";

    public GuaranteeNumberTakenException() {
        super(CODE, Kind.CONFLICT);
    }
}
