package com.iturtle.core.exception;

/**
 * 설정이 없거나 잘못되었거나 서로 충돌한다. 애플리케이션 시작 시 던져 잘못된 설정으로 뜨지 않게 한다.
 * <p>
 * 요청 처리 중의 실패가 아니므로 BusinessException 이 아니라 IllegalStateException 을 상속한다.
 * 메시지에는 문제가 된 설정 키와 고치는 방법을 적는다.
 */
public class InvalidConfigurationException extends IllegalStateException {

    public InvalidConfigurationException(String message) {
        super(message);
    }

    public InvalidConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
