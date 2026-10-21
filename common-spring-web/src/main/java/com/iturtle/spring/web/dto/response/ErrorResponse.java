package com.iturtle.spring.web.dto.response;

import com.iturtle.core.exception.ErrorCode;

import java.util.List;

/**
 * errors 는 입력값 검증 실패일 때만 채워지고, 그 외에는 null 이다.
 */
public record ErrorResponse(
        int status,
        String code,
        String message,
        List<FieldErrorResponse> errors
) {

    public ErrorResponse(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ErrorResponse(ErrorCode errorCode, List<FieldErrorResponse> errors) {
        this(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(), errors);
    }

}
