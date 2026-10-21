package com.iturtle.core.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdGenerateUtilTest {

    private final IdGenerateUtils idGenerateUtil = new IdGenerateUtils();

    @Test
    void uuidV7() throws InterruptedException {
        UUID first = idGenerateUtil.getUuidV7();
        Thread.sleep(2);
        UUID second = idGenerateUtil.getUuidV7();

        assertEquals(7, first.version());
        assertEquals(2, first.variant());
        assertTrue(first.toString().compareTo(second.toString()) < 0, "v7 은 시간순 정렬되어야 함");
    }

    @Test
    void uuidV4() {
        UUID uuid = idGenerateUtil.getUuidV4();
        assertEquals(4, uuid.version());
        assertEquals(2, uuid.variant());
    }

    @Test
    void hex() {
        assertTrue(idGenerateUtil.getHex8().matches("[0-9a-f]{8}"));
        assertTrue(idGenerateUtil.getHex16().matches("[0-9a-f]{16}"));
        assertTrue(idGenerateUtil.getHex32().matches("[0-9a-f]{32}"));
        assertTrue(idGenerateUtil.getHex64().matches("[0-9a-f]{64}"));
    }
}
