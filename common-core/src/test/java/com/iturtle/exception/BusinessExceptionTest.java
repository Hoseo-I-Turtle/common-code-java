package com.iturtle.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

    @Test
    void defaultMessageFromErrorCode() {
        BusinessException e = new BusinessException(CommonErrorCode.NOT_FOUND);

        assertSame(CommonErrorCode.NOT_FOUND, e.getErrorCode());
        assertEquals(CommonErrorCode.NOT_FOUND.getMessage(), e.getMessage());
        assertEquals(404, e.getErrorCode().getStatus());
    }

    @Test
    void cause() {
        IllegalStateException cause = new IllegalStateException("db down");
        BusinessException e = new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR, cause);

        assertEquals(CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage(), e.getMessage());
        assertSame(cause, e.getCause());
    }
}
