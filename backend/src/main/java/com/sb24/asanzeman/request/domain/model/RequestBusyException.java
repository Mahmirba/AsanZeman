package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** Another user is acting on the request at this moment, so its row lock is taken (4.1): 409 «این درخواست در حال اقدام توسط کاربر دیگری است. لطفاً چند لحظه بعد دوباره تلاش کنید.» */
public class RequestBusyException extends DomainException {

    public static final String CODE = "request.busy";

    public RequestBusyException() {
        super(CODE, Kind.CONFLICT);
    }
}
