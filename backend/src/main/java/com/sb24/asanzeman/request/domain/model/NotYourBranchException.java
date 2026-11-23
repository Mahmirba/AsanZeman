package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/** The request belongs to another branch (4.4): only its own branch or the branch that replaced it may act: 403. */
public class NotYourBranchException extends DomainException {

    public static final String CODE = "request.branch-forbidden";

    public NotYourBranchException() {
        super(CODE, Kind.FORBIDDEN);
    }
}
