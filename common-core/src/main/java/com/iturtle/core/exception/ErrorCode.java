package com.iturtle.core.exception;

/**
 * 에러 코드 규약. 각 서비스는 이 인터페이스를 구현한 enum 으로 도메인 에러를 정의한다.
 * core 는 프레임워크 비의존이므로 HTTP 상태는 int 로 표현한다.
 */
public interface ErrorCode {

    int getStatus();

    String getCode();

    String getMessage();
}
