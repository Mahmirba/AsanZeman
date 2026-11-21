package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The request is already rejected, cancelled, revoked or completed, so there is nothing to cancel (4.2): 409 «برای این درخواست امکان لغو یا ابطال وجود ندارد.» */
public class RequestNotCancellableException extends DomainException {

    public static final String CODE = "request.not-cancellable";

    public RequestNotCancellableException() {
        super(CODE, Kind.CONFLICT);
    }
}
