package com.iturtle.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaskingUtilsTest {

    @Test
    void name() {
        assertEquals("홍", MaskingUtils.name("홍"));
        assertEquals("홍*", MaskingUtils.name("홍길"));
        assertEquals("홍*동", MaskingUtils.name("홍길동"));
        assertEquals("남**수", MaskingUtils.name("남궁민수"));
    }

    @Test
    void phone() {
        assertEquals("010-****-5678", MaskingUtils.phone("010-1234-5678"));
        assertEquals("010****5678", MaskingUtils.phone("01012345678"));
        assertEquals("02-***-5678", MaskingUtils.phone("02-123-5678"));
    }

    @Test
    void email() {
        assertEquals("ab****@gmail.com", MaskingUtils.email("abcdef@gmail.com"));
        assertEquals("a*@gmail.com", MaskingUtils.email("ab@gmail.com"));
    }

    @Test
    void rrn() {
        assertEquals("900101-1******", MaskingUtils.rrn("900101-1234567"));
        assertEquals("9001011******", MaskingUtils.rrn("9001011234567"));
    }

    @Test
    void cardNumber() {
        assertEquals("1234-****-****-3456", MaskingUtils.cardNumber("1234-5678-9012-3456"));
        assertEquals("1234********3456", MaskingUtils.cardNumber("1234567890123456"));
    }

    @Test
    void accountNumber() {
        assertEquals("***-***-**6789", MaskingUtils.accountNumber("110-123-456789"));
    }

    @Test
    void mask() {
        assertEquals("ab**ef", MaskingUtils.mask("abcdef", 2, 4));
        assertEquals("ab****", MaskingUtils.mask("abcdef", 2, 100));
    }

    @Test
    void nullAndBlank() {
        assertNull(MaskingUtils.name(null));
        assertEquals("", MaskingUtils.phone(""));
    }
}
