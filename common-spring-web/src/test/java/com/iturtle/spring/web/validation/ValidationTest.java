package com.iturtle.spring.web.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void phone() {
        assertTrue(validator.validate(new PhoneRequest("010-1234-5678")).isEmpty());
        assertTrue(validator.validate(new PhoneRequest("01012345678")).isEmpty());
        assertTrue(validator.validate(new PhoneRequest("02-123-4567")).isEmpty());
        assertTrue(validator.validate(new PhoneRequest(null)).isEmpty());
        assertFalse(validator.validate(new PhoneRequest("010-12-5678")).isEmpty());
        assertFalse(validator.validate(new PhoneRequest("abc")).isEmpty());
    }

    @Test
    void enumValue() {
        assertTrue(validator.validate(new StatusRequest("PAID", "paid")).isEmpty());
        assertTrue(validator.validate(new StatusRequest(null, null)).isEmpty());
        assertEquals(1, validator.validate(new StatusRequest("paid", "PAID")).size());
        assertEquals(1, validator.validate(new StatusRequest("PAID", "unknown")).size());
    }

    enum OrderStatus { PAID, CANCELED }

    record PhoneRequest(@Phone String phone) {
    }

    record StatusRequest(
            @EnumValue(enumClass = OrderStatus.class) String status,
            @EnumValue(enumClass = OrderStatus.class, ignoreCase = true) String statusIgnoreCase
    ) {
    }
}
