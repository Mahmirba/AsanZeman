package com.sb24.asanzeman.auth.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** personnelInfo does not know the user: 403 «اطلاعات پرسنلی شما یافت نشد. لطفاً با راهبر سامانه تماس بگیرید.» (6.2) */
public class PersonnelNotFoundException extends DomainException {

    public static final String CODE = "auth.bank.personnel-not-found";

    public PersonnelNotFoundException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
