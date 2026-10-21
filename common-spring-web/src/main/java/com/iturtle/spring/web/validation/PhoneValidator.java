package com.iturtle.spring.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PhoneValidator implements ConstraintValidator<Phone, String> {

    private static final Pattern PHONE = Pattern.compile("^\\d{2,3}-?\\d{3,4}-?\\d{4}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || PHONE.matcher(value).matches();
    }
}
