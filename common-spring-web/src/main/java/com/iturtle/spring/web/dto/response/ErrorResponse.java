package com.iturtle.spring.web.dto.response;

import com.iturtle.core.exception.ErrorCode;

import java.util.List;

/**
 * errors 는 입력값 검증 실패일 때만 채워지고, 그 외에는 null 이다.
 * traceId 는 로그 추적용이며 추적이 비활성화되어 있으면 null 이다.
 */
public record ErrorResponse(
        int status,
        String code,
        String message,
        List<FieldErrorResponse> errors,
        String traceId
) {

    public ErrorResponse(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public ErrorResponse(ErrorCode errorCode, List<FieldErrorResponse> errors, String traceId) {
        this(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage(), errors, traceId);
    }

}
