package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.DomainException;

/**
 * The 9999 tracking codes of one branch, agency and day are used up (D12): 409. Practically unreachable, but the
 * counter must never wrap and make a code twice.
 */
public class TrackingCodesExhaustedException extends DomainException {

    public static final String CODE = "request.tracking-codes-exhausted";

    public TrackingCodesExhaustedException() {
        super(CODE, Kind.CONFLICT);
    }
}
