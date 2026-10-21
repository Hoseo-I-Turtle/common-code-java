package com.iturtle.core.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CommonErrorCodeTest {

    @Test
    void fromStatus() {
        assertEquals(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, CommonErrorCode.fromStatus(415));
        assertEquals(CommonErrorCode.INVALID_INPUT, CommonErrorCode.fromStatus(418));
        assertEquals(CommonErrorCode.INTERNAL_SERVER_ERROR, CommonErrorCode.fromStatus(503));
    }

    @Test
    void statusesAreUnique() {
        long distinct = Arrays.stream(CommonErrorCode.values()).mapToInt(CommonErrorCode::getStatus).distinct().count();

        assertEquals(CommonErrorCode.values().length, distinct);
    }
}
