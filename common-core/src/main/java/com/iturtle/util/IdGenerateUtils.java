package com.iturtle.util;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

public final class IdGenerateUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    /**
     * RFC 9562 UUID v7 (48bit unix epoch millis + 74bit random). 시간순 정렬 가능.
     */
    public UUID getUuidV7(){
        long timestamp = System.currentTimeMillis();

        long msb = (timestamp & 0xFFFF_FFFF_FFFFL) << 16;       // unix_ts_ms (48bit)
        msb |= 0x7000L;                                          // version 7
        msb |= RANDOM.nextInt(1 << 12);                          // rand_a (12bit)

        long lsb = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;   // rand_b (62bit)
        lsb |= 0x8000_0000_0000_0000L;                           // variant 10

        return new UUID(msb, lsb);
    }

    /**
     * RFC 9562 UUID v4 (random).
     */
    public UUID getUuidV4(){
        return UUID.randomUUID();
    }

    /** 8자리 hex 문자열 (4byte random) */
    public String getHex8() {
        return randomHex(4);
    }

    /** 16자리 hex 문자열 (8byte random) */
    public String getHex16() {
        return randomHex(8);
    }

    /** 32자리 hex 문자열 (16byte random) */
    public String getHex32() {
        return randomHex(16);
    }

    /** 64자리 hex 문자열 (32byte random) */
    public String getHex64() {
        return randomHex(32);
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

}
