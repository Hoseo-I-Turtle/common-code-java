package com.iturtle.spring.security.jwt;

import com.iturtle.core.exception.BusinessException;

/**
 * 토큰 검증 실패. 만료면 EXPIRED_TOKEN, 그 외(서명 불일치, 형식 오류, issuer 불일치 등)는 INVALID_TOKEN 을 담는다.
 * BusinessException 이므로 컨트롤러 안에서 던지면 전역 예외 핸들러가 401 로 응답한다.
 */
public class InvalidJwtException extends BusinessException {

    public InvalidJwtException(JwtErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public InvalidJwtException(JwtErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public boolean isExpired() {
        return getErrorCode() == JwtErrorCode.EXPIRED_TOKEN;
    }
}
