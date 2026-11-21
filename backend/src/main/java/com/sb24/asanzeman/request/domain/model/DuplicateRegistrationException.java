package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The same agency registered the same customer a moment ago (D47): 409 «درخواست مشابهی چند لحظه پیش ثبت شده است. لطفاً فهرست درخواست‌ها را بررسی کنید.» */
public class DuplicateRegistrationException extends DomainException {

    public static final String CODE = "request.duplicate-registration";

    public DuplicateRegistrationException() {
        super(CODE, Kind.CONFLICT);
    }
}
