package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.JalaliDate;

/**
 * The tracking code of a request (D12): the <b>full</b> branch code as Yaghut returns it, the agency code (4 digits),
 * the Jalali registration day as {@code YYMMDD} and a 4-digit counter. Everything after the branch code has a fixed
 * width, so two different parts can never make the same code. It is 17 to 19 digits for the usual 3 to 5 digit branch
 * code and is stored as {@code VARCHAR2(20)}.
 */
public record TrackingCode(String value) {

    static final int MAX_LENGTH = 20;
    static final int MAX_COUNTER = 9999;

    public TrackingCode {
        // at least 1 branch digit + 4 agency + 6 day + 4 counter
        if (value == null || !value.matches("\\d{15," + MAX_LENGTH + "}")) {
            throw new IllegalArgumentException("not a tracking code: " + value);
        }
    }

    /**
     * @param branchCode  digits only, as Yaghut sends it, leading zeros kept
     * @param agencyCode  exactly 4 digits
     * @param counter     1 to 9999, the request's number for that branch, agency and day
     */
    public static TrackingCode of(String branchCode, String agencyCode, JalaliDate day, int counter) {
        if (branchCode == null || !branchCode.matches("\\d+")) {
            throw new IllegalArgumentException("a tracking code needs a numeric branch code: " + branchCode);
        }
        if (agencyCode == null || !agencyCode.matches("\\d{4}")) {
            throw new IllegalArgumentException("a tracking code needs a 4-digit agency code: " + agencyCode);
        }
        if (counter < 1 || counter > MAX_COUNTER) {
            throw new IllegalArgumentException("the counter must be 1 to " + MAX_COUNTER + ": " + counter);
        }
        return new TrackingCode(branchCode + agencyCode + day.toYyMmDd() + String.format("%04d", counter));
    }

    @Override
    public String toString() {
        return value;
    }
}
