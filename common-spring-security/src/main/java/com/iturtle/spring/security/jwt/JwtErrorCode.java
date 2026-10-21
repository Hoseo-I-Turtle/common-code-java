package com.iturtle.spring.security.jwt;

import com.iturtle.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 토큰 검증 실패 코드. 클라이언트가 만료(재발급 필요)와 위조 / 형식 오류를 구분할 수 있게 나눈다.
 * CommonErrorCode 는 상태 코드가 항목마다 달라야 하므로 401 을 세분화하는 코드는 여기에 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum JwtErrorCode implements ErrorCode {

    EXPIRED_TOKEN(401, "J001", "만료된 토큰입니다."),
    INVALID_TOKEN(401, "J002", "유효하지 않은 토큰입니다.");

    private final int status;
    private final String code;
    private final String message;
}
