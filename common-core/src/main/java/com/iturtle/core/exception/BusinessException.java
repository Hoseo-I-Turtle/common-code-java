package com.iturtle.core.exception;

import lombok.Getter;

/**
 * 비즈니스 예외의 최상위 클래스. 모든 도메인 예외는 이 클래스를 상속하거나 ErrorCode 와 함께 직접 던진다.
 * 응답에는 항상 ErrorCode 의 메시지가 나가고, message 를 지정하면 로그에만 남는다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
