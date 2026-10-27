package com.iturtle.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenHashUtilsTest {

    @Test
    void sha256Hex() {
        // echo -n "abc" | sha256sum
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenHashUtils.sha256Hex("abc"));
        assertEquals(64, TokenHashUtils.sha256Hex("any token").length());
    }

    @Test
    void matches() {
        String hash = TokenHashUtils.sha256Hex("token-1");

        assertTrue(TokenHashUtils.matches("token-1", hash));
        assertFalse(TokenHashUtils.matches("token-2", hash));
        assertFalse(TokenHashUtils.matches(null, hash));
        assertFalse(TokenHashUtils.matches("token-1", null));
    }
}
