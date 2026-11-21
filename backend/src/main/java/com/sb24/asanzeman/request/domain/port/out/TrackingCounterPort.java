package com.sb24.asanzeman.request.domain.port.out;

/** The {@code tracking_counter} table (D12): one counter per branch, agency and Jalali day. */
public interface TrackingCounterPort {

    /**
     * Takes the next number, under a row lock, inside the caller's transaction (so a registration that fails
     * afterwards gives the number back).
     *
     * @param day the Jalali day as {@code YYMMDD}
     * @return 1 to 9999
     * @throws com.sb24.asanzeman.request.domain.model.TrackingCodesExhaustedException when 9999 are used
     */
    int next(String branchCode, String agencyCode, String day);
}
