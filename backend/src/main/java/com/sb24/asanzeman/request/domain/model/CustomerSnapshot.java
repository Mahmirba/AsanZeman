package com.sb24.asanzeman.request.domain.model;

import java.time.LocalDate;

/**
 * The customer's details as Yaghut {@code customerDetailInfo} gave them when the agency registered the request
 * (6.1, D21). Stored in plain text (D2, D22) and used for the whole life of the request, never refreshed. Gender is
 * kept but not used: the inquiries take gender and birth date from Sabte Ahval (D26).
 *
 * @param code        the national code, 10 digits
 * @param personality {@code ACTUAL} for a natural person
 */
public record CustomerSnapshot(String cif, String code, String firstName, String lastName, String fullName,
        String fatherName, LocalDate birthDate, String mobile, String ssn, String certificateSerial,
        String certificateSeries, String personality, String gender) {

    public CustomerSnapshot {
        if (code == null || !code.matches("\\d{10}")) {
            throw new IllegalArgumentException("a customer needs a 10-digit national code");
        }
    }

    public boolean isActual() {
        return "ACTUAL".equals(personality);
    }

    /** @return whole years of age on {@code today}; {@code birthDate} must be known */
    public int ageOn(LocalDate today) {
        return java.time.Period.between(birthDate, today).getYears();
    }
}
