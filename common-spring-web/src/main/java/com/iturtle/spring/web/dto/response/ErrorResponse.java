package com.iturtle.spring.web.dto.response;

import com.iturtle.core.exception.ErrorCode;

public record ErrorResponse(
        int status,
        String code,
        String message
) {

    public ErrorResponse(ErrorCode errorCode) {
        this(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage());
    }

}
