package com.sb24.asanzeman.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Bean Validation form of D30. Null is left to {@code @NotNull}; everything else must be a valid national code. */
@Documented
@Constraint(validatedBy = IranianNationalCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface IranianNationalCode {

    String message() default "{azs.validation.national-code}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
