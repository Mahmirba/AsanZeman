package com.sb24.asanzeman.request.domain.port.in;

import java.time.LocalDate;

/** A companion as the agency typed it; dates are already Gregorian. */
public record CompanionInput(String fullName, String nationalCode, LocalDate birthDate, String passportNumber) {
}
