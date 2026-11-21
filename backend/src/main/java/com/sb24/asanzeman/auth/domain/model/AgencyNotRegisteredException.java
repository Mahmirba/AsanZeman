package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** No active agency has this Yaghut cif: 403 «آژانس شما در سامانه آسان ضمان ثبت نشده یا غیرفعال است. لطفاً با راهبر سامانه تماس بگیرید.» (5.4) */
public class AgencyNotRegisteredException extends DomainException {

    public static final String CODE = "auth.agency.not-registered";

    public AgencyNotRegisteredException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
