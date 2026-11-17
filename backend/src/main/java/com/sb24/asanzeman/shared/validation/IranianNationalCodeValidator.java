package com.sb24.asanzeman.shared.validation;

import com.sb24.asanzeman.shared.model.NationalCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IranianNationalCodeValidator implements ConstraintValidator<IranianNationalCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || NationalCode.parse(value).isPresent();
    }
}
