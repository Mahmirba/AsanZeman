package com.sb24.asanzeman.inquiry.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The customer tried again too soon after a blocked run («تلاش مجدد» has a cooldown, 5.1): 409. */
public class InquiryCooldownException extends DomainException {

    public static final String CODE = "inquiry.cooldown";

    public InquiryCooldownException(long secondsLeft) {
        super(CODE, Kind.CONFLICT, secondsLeft);
    }
}
