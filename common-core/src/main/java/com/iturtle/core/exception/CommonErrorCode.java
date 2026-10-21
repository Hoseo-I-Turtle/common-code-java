package com.iturtle.core.exception;

import lombok.Getter;

/**
 * 서비스 공통 에러 코드. C 접두사는 공통 코드 전용이므로 도메인 에러 코드는 다른 접두사를 사용한다.
 * 전역 예외 핸들러가 Spring 예외의 상태 코드를 이 enum 에서 찾으므로 상태 코드는 항목마다 달라야 한다.
 */
@Getter
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(400, "C001", "잘못된 입력값입니다."),
    UNAUTHORIZED(401, "C002", "인증이 필요합니다."),
    FORBIDDEN(403, "C003", "접근 권한이 없습니다."),
    NOT_FOUND(404, "C004", "리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(405, "C005", "지원하지 않는 HTTP 메서드입니다."),
    CONFLICT(409, "C006", "리소스가 충돌합니다."),
    NOT_ACCEPTABLE(406, "C007", "지원하지 않는 응답 형식입니다."),
    PAYLOAD_TOO_LARGE(413, "C008", "요청 크기가 너무 큽니다."),
    UNSUPPORTED_MEDIA_TYPE(415, "C009", "지원하지 않는 Content-Type 입니다."),
    EXTERNAL_API_ERROR(502, "C010", "외부 서비스 호출에 실패했습니다."),
    INTERNAL_SERVER_ERROR(500, "C999", "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;

    CommonErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    /**
     * 같은 상태 코드의 항목을 찾는다. 없으면 4xx 는 INVALID_INPUT, 그 외는 INTERNAL_SERVER_ERROR.
     */
    public static CommonErrorCode fromStatus(int status) {
        for (CommonErrorCode errorCode : values()) {
            if (errorCode.status == status) {
                return errorCode;
            }
        }
        return status >= 400 && status < 500 ? INVALID_INPUT : INTERNAL_SERVER_ERROR;
    }
}
