package com.iturtle.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilsTest {

    @Test
    void sha256Hex() {
        // echo -n "abc" | sha256sum
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", HashUtils.sha256Hex("abc"));
        assertEquals(64, HashUtils.sha256Hex("any token").length());
    }

    @Test
    void matchesSha256Hex() {
        String hash = HashUtils.sha256Hex("token-1");

        assertTrue(HashUtils.matchesSha256Hex("token-1", hash));
        assertFalse(HashUtils.matchesSha256Hex("token-2", hash));
        assertFalse(HashUtils.matchesSha256Hex(null, hash));
        assertFalse(HashUtils.matchesSha256Hex("token-1", null));
    }
}
