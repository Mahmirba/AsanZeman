package com.sb24.asanzeman.inquiry.domain.model;

import java.time.LocalDate;
import java.time.Period;

/**
 * What the inquiries need to know about the request (5.1, 5.3). {@code civil} is {@code null} until Sabte Ahval has
 * answered; stage 2 only runs once it has.
 */
public record InquiryContext(String nationalCode, String mobile, long amount, String branchCode, CivilData civil) {

    public InquiryContext(String nationalCode, String mobile, long amount, String branchCode) {
        this(nationalCode, mobile, amount, branchCode, null);
    }

    public InquiryContext withCivil(CivilData data) {
        return new InquiryContext(nationalCode, mobile, amount, branchCode, data);
    }

    /** The customer's age in whole years on {@code today}, from Sabte Ahval's birth date. */
    public int ageOn(LocalDate today) {
        return Period.between(civil.birthDate(), today).getYears();
    }
}
