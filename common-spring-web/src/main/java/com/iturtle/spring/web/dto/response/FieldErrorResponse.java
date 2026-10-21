package com.iturtle.spring.web.dto.response;

/**
 * 검증 실패 항목. 민감 정보가 노출될 수 있으므로 입력값(rejectedValue)은 담지 않는다.
 */
public record FieldErrorResponse(
        String field,
        String reason
) {
}
