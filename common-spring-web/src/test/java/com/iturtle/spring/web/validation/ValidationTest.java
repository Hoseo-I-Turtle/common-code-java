package com.iturtle.spring.web.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"010-1234-5678", "01012345678", "011-123-4567", "02-123-4567", "0212345678",
            "031-1234-5678", "064-123-4567", "070-1234-5678", "0505-123-4567", "1588-1234", "16001234"})
    void validPhones(String phone) {
        assertTrue(validator.validate(new PhoneRequest(phone, null)).isEmpty(), phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {"010-12-5678", "abc", "123-456-7890", "012-1234-5678", "039-123-4567", "1234-5678", "070-123-4567"})
    void invalidPhones(String phone) {
        assertFalse(validator.validate(new PhoneRequest(phone, null)).isEmpty(), phone);
    }

    @Test
    void mobileTypeAcceptsOnlyMobile() {
        assertTrue(validator.validate(new PhoneRequest(null, "010-1234-5678")).isEmpty());
        assertFalse(validator.validate(new PhoneRequest(null, "02-123-4567")).isEmpty());
        assertFalse(validator.validate(new PhoneRequest(null, "1588-1234")).isEmpty());
    }

    @Test
    void nullPhonePasses() {
        assertTrue(validator.validate(new PhoneRequest(null, null)).isEmpty());
    }

    @Test
    void enumValue() {
        assertTrue(validator.validate(new StatusRequest("PAID", "paid")).isEmpty());
        assertTrue(validator.validate(new StatusRequest(null, null)).isEmpty());
        assertEquals(1, validator.validate(new StatusRequest("paid", "PAID")).size());
        assertEquals(1, validator.validate(new StatusRequest("PAID", "unknown")).size());
    }

    enum OrderStatus { PAID, CANCELED }

    record PhoneRequest(@Phone String phone, @Phone(type = Phone.Type.MOBILE) String mobile) {
    }

    record StatusRequest(
            @EnumValue(enumClass = OrderStatus.class) String status,
            @EnumValue(enumClass = OrderStatus.class, ignoreCase = true) String statusIgnoreCase
    ) {
    }
}
