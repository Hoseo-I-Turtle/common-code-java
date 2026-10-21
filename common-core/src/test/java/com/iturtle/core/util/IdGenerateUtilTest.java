package com.iturtle.core.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdGenerateUtilTest {

    @Test
    void uuidV7() throws InterruptedException {
        UUID first = IdGenerateUtils.getUuidV7();
        Thread.sleep(2);
        UUID second = IdGenerateUtils.getUuidV7();

        assertEquals(7, first.version());
        assertEquals(2, first.variant());
        assertTrue(first.toString().compareTo(second.toString()) < 0, "v7 은 시간순 정렬되어야 함");
    }

    @Test
    void uuidV4() {
        UUID uuid = IdGenerateUtils.getUuidV4();
        assertEquals(4, uuid.version());
        assertEquals(2, uuid.variant());
    }

    @Test
    void hex() {
        assertTrue(IdGenerateUtils.getHex8().matches("[0-9a-f]{8}"));
        assertTrue(IdGenerateUtils.getHex16().matches("[0-9a-f]{16}"));
        assertTrue(IdGenerateUtils.getHex32().matches("[0-9a-f]{32}"));
        assertTrue(IdGenerateUtils.getHex64().matches("[0-9a-f]{64}"));
    }
}
