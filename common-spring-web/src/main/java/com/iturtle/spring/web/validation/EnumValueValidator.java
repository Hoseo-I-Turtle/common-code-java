package com.iturtle.spring.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumValueValidator implements ConstraintValidator<EnumValue, String> {

    private Set<String> names;
    private boolean ignoreCase;

    @Override
    public void initialize(EnumValue annotation) {
        this.ignoreCase = annotation.ignoreCase();
        this.names = Arrays.stream(annotation.enumClass().getEnumConstants())
                .map(constant -> normalize(constant.name()))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || names.contains(normalize(value));
    }

    private String normalize(String value) {
        return ignoreCase ? value.toUpperCase(Locale.ROOT) : value;
    }
}
