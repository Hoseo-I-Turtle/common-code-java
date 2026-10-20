package com.iturtle.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaskingUtilsTest {

    private final MaskingUtils maskingUtils = new MaskingUtils();

    @Test
    void name() {
        assertEquals("홍", maskingUtils.name("홍"));
        assertEquals("홍*", maskingUtils.name("홍길"));
        assertEquals("홍*동", maskingUtils.name("홍길동"));
        assertEquals("남**수", maskingUtils.name("남궁민수"));
    }

    @Test
    void phone() {
        assertEquals("010-****-5678", maskingUtils.phone("010-1234-5678"));
        assertEquals("010****5678", maskingUtils.phone("01012345678"));
        assertEquals("02-***-5678", maskingUtils.phone("02-123-5678"));
    }

    @Test
    void email() {
        assertEquals("ab****@gmail.com", maskingUtils.email("abcdef@gmail.com"));
        assertEquals("a*@gmail.com", maskingUtils.email("ab@gmail.com"));
    }

    @Test
    void rrn() {
        assertEquals("900101-1******", maskingUtils.rrn("900101-1234567"));
        assertEquals("9001011******", maskingUtils.rrn("9001011234567"));
    }

    @Test
    void cardNumber() {
        assertEquals("1234-****-****-3456", maskingUtils.cardNumber("1234-5678-9012-3456"));
        assertEquals("1234********3456", maskingUtils.cardNumber("1234567890123456"));
    }

    @Test
    void accountNumber() {
        assertEquals("***-***-**6789", maskingUtils.accountNumber("110-123-456789"));
    }

    @Test
    void mask() {
        assertEquals("ab**ef", maskingUtils.mask("abcdef", 2, 4));
        assertEquals("ab****", maskingUtils.mask("abcdef", 2, 100));
    }

    @Test
    void nullAndBlank() {
        assertNull(maskingUtils.name(null));
        assertEquals("", maskingUtils.phone(""));
    }
}
