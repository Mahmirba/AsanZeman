package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The chosen branch is unknown, inactive or closed (4.4): 400 «شعبه انتخاب‌شده معتبر نیست.» */
public class BranchNotAvailableException extends DomainException {

    public static final String CODE = "request.branch-not-available";

    public BranchNotAvailableException() {
        super(CODE, Kind.BAD_REQUEST);
    }
}
