package com.sb24.asanzeman.inquiry.domain.model;

import java.time.LocalDate;

/** What Sabte Ahval says about the customer: the authoritative gender and birth date (D26). */
public record CivilData(Gender gender, LocalDate birthDate) {

    public CivilData {
        if (gender == null || birthDate == null) {
            throw new IllegalArgumentException("civil data needs a gender and a birth date");
        }
    }
}
