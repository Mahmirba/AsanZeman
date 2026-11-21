package com.sb24.asanzeman.request.domain.port.in;

import java.time.LocalDate;

/** What the registration form shows after the agency searches a national code (SRS UC-AZS-8, steps 5 to 7). */
public record CustomerLookupResult(String cif, String nationalCode, String fullName, LocalDate birthDate,
        String mobile) {
}
