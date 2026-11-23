package com.sb24.asanzeman.inquiry.domain.model;

import java.time.LocalDate;
import java.time.Period;

/**
 * What the inquiries need to know about the request (5.1, 5.3). {@code claimedBirthDate} is the birth date saved with
 * the request (from Yaghut): only Sabte Ahval uses it, to check it against the registry. {@code civil} is
 * {@code null} until Sabte Ahval has answered; the gender and birth date every later inquiry uses come from it (D26).
 */
public record InquiryContext(String nationalCode, String mobile, long amount, String branchCode,
        LocalDate claimedBirthDate, CivilData civil) {

    public InquiryContext(String nationalCode, String mobile, long amount, String branchCode) {
        this(nationalCode, mobile, amount, branchCode, null, null);
    }

    public InquiryContext(String nationalCode, String mobile, long amount, String branchCode,
            LocalDate claimedBirthDate) {
        this(nationalCode, mobile, amount, branchCode, claimedBirthDate, null);
    }

    public InquiryContext withCivil(CivilData data) {
        return new InquiryContext(nationalCode, mobile, amount, branchCode, claimedBirthDate, data);
    }

    /** The customer's age in whole years on {@code today}, from Sabte Ahval's birth date. */
    public int ageOn(LocalDate today) {
        return Period.between(civil.birthDate(), today).getYears();
    }
}
