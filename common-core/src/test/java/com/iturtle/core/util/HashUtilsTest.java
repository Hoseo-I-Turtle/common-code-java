package com.iturtle.core.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

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

    @Test
    void sha256HexOfStreamEqualsStringHash() {
        assertEquals(HashUtils.sha256Hex("abc"),
                HashUtils.sha256Hex(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))));
        // 버퍼(8KB)보다 큰 내용
        byte[] big = new byte[20_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) i;
        }
        assertEquals(64, HashUtils.sha256Hex(new ByteArrayInputStream(big)).length());
        // 빈 내용: echo -n "" | sha256sum
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HashUtils.sha256Hex(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void sha256HexOfStreamWrapsReadFailure() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        assertThrows(UncheckedIOException.class, () -> HashUtils.sha256Hex(broken));
    }

    @Test
    void hmacSha256Hex() {
        // RFC 4231 Test Case 2
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                HashUtils.hmacSha256Hex("Jefe", "what do ya want for nothing?"));
        assertThrows(IllegalArgumentException.class, () -> HashUtils.hmacSha256Hex(new byte[0], new byte[]{1}));
    }

    @Test
    void matchesHmacSha256Hex() {
        byte[] secret = "webhook-secret".getBytes(StandardCharsets.UTF_8);
        byte[] body = "{\"zen\":\"Keep it logically awesome.\"}".getBytes(StandardCharsets.UTF_8);
        String signature = HashUtils.hmacSha256Hex(secret, body);

        assertTrue(HashUtils.matchesHmacSha256Hex(secret, body, signature));
        assertTrue(HashUtils.matchesHmacSha256Hex(secret, body, signature.toUpperCase()));
        assertFalse(HashUtils.matchesHmacSha256Hex(secret, "{}".getBytes(StandardCharsets.UTF_8), signature));
        assertFalse(HashUtils.matchesHmacSha256Hex("other".getBytes(StandardCharsets.UTF_8), body, signature));
        assertFalse(HashUtils.matchesHmacSha256Hex(secret, body, "not-hex"));
        assertFalse(HashUtils.matchesHmacSha256Hex(secret, body, signature.substring(2)));
        assertFalse(HashUtils.matchesHmacSha256Hex(secret, body, null));
        assertFalse(HashUtils.matchesHmacSha256Hex(null, body, signature));
        assertFalse(HashUtils.matchesHmacSha256Hex(new byte[0], body, signature));
    }

    @Test
    void constantTimeEquals() {
        assertTrue(HashUtils.constantTimeEquals("abc", "abc"));
        assertFalse(HashUtils.constantTimeEquals("abc", "abd"));
        assertFalse(HashUtils.constantTimeEquals("abc", "abcd"));
        assertFalse(HashUtils.constantTimeEquals(null, "abc"));
        assertFalse(HashUtils.constantTimeEquals("abc", null));
    }
}
