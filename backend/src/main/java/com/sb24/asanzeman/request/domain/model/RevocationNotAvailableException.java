package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The branch has already started on the request, so it needs a revocation request (4.2). The branch side is not built yet (Phase 4 and 5): 409 «ثبت درخواست ابطال هنوز فعال نیست. لطفاً با شعبه تماس بگیرید.» */
public class RevocationNotAvailableException extends DomainException {

    public static final String CODE = "request.revocation-not-available";

    public RevocationNotAvailableException() {
        super(CODE, Kind.CONFLICT);
    }
}
