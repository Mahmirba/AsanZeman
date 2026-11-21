package com.sb24.asanzeman.integration.yaghut;

import java.time.LocalDate;

/**
 * What Yaghut {@code customerDetailInfo} says about a customer (5.4, 6.1): the snapshot saved with a request. All
 * fields may be {@code null}; the caller decides what it needs. {@code personalityType} is {@code ACTUAL} for a
 * natural person.
 *
 * <p><b>Assumption to confirm in UAT:</b> the JSON key names are read in {@link YaghutClient#customerDetailInfo};
 * the plan lists the fields but not their wire names.
 */
public record YaghutCustomer(
        String cif,
        String code,
        String firstName,
        String lastName,
        String fullName,
        String fatherName,
        LocalDate birthDate,
        String mobile,
        String ssn,
        String certificateSerial,
        String certificateSeries,
        String personalityType,
        String gender) {
}
