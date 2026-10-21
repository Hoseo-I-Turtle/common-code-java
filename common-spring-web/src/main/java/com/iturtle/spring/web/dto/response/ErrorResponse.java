package com.iturtle.spring.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iturtle.core.exception.ErrorCode;

import java.util.List;

/**
 * errors 는 입력값 검증 실패일 때만, traceId 는 추적이 활성화되어 있을 때만 채워진다. 값이 없으면 JSON 에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
