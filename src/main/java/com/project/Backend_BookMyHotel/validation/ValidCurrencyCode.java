package com.project.Backend_BookMyHotel.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Currency;
import java.util.Locale;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCurrencyCode.CurrencyCodeValidator.class)
public @interface ValidCurrencyCode {
    String message() default "must be a valid ISO 4217 currency code";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class CurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isBlank()) {
                return true; // presence is enforced separately by @NotBlank
            }
            try {
                Currency.getInstance(value.toUpperCase(Locale.ROOT));
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
