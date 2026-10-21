package com.iturtle.spring.core.mdc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MdcContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void defaultsAndPut() {
        MdcContext context = new MdcContext(Map.of("app.region", "kr"))
                .put("user.id", "u-1")
                .put("ignored", null)
                .put(" ", "x");

        assertEquals(Map.of("app.region", "kr", "user.id", "u-1"), context.getEntries());
    }

    @Test
    void putOverridesDefault() {
        MdcContext context = new MdcContext(Map.of("user.id", "default")).put("user.id", "u-1");

        assertEquals("u-1", context.getEntries().get("user.id"));
    }

    @Test
    void openAndClose() {
        MdcContext context = new MdcContext().put("user.id", "u-1");

        try (MdcContext ignored = context.open()) {
            assertEquals("u-1", MDC.get("user.id"));
        }
        assertNull(MDC.get("user.id"));
    }

    @Test
    void closeRestoresPreviousValueAndKeepsOtherKeys() {
        MDC.put("user.id", "outer");
        MDC.put("traceId", "t-1");

        try (MdcContext ignored = new MdcContext().put("user.id", "inner").open()) {
            assertEquals("inner", MDC.get("user.id"));
        }
        assertEquals("outer", MDC.get("user.id"));
        assertEquals("t-1", MDC.get("traceId"));
    }
}
