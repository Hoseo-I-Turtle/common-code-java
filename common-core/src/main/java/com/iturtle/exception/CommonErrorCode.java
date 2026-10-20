package com.iturtle.exception;

public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(400, "C001", "잘못된 입력값입니다."),
    UNAUTHORIZED(401, "C002", "인증이 필요합니다."),
    FORBIDDEN(403, "C003", "접근 권한이 없습니다."),
    NOT_FOUND(404, "C004", "리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(405, "C005", "지원하지 않는 HTTP 메서드입니다."),
    CONFLICT(409, "C006", "리소스가 충돌합니다."),
    INTERNAL_SERVER_ERROR(500, "C999", "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;

    CommonErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
