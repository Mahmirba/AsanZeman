package com.sb24.asanzeman.request.domain.model;

import com.sb24.asanzeman.shared.model.NationalCode;
import java.time.LocalDate;

/**
 * A person travelling with the customer (SRS UC-AZS-8, D52). Every field is required: name up to 100 characters,
 * a valid 10-digit national code, birth date and passport number.
 */
public record Companion(String fullName, String nationalCode, LocalDate birthDate, String passportNumber) {

    static final int MAX_NAME = 100;
    static final int MAX_PASSPORT = 30;

    public Companion {
        fullName = fullName == null ? null : fullName.trim();
        passportNumber = passportNumber == null ? null : passportNumber.trim();
        if (fullName == null || fullName.isEmpty() || fullName.length() > MAX_NAME) {
            throw new InvalidRequestDataException("companion name must be 1 to " + MAX_NAME + " characters");
        }
        if (!NationalCode.isValid(nationalCode)) {
            throw new InvalidRequestDataException("companion national code is not valid");
        }
        if (birthDate == null) {
            throw new InvalidRequestDataException("companion birth date is required");
        }
        if (passportNumber == null || passportNumber.isEmpty() || passportNumber.length() > MAX_PASSPORT) {
            throw new InvalidRequestDataException("companion passport number must be 1 to " + MAX_PASSPORT
                    + " characters");
        }
    }
}
