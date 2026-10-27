package com.iturtle.jpa.entity.messaging;

/**
 * Outbox 이벤트의 발행 진행 상황.
 */
public enum OutboxStatus {

    /**
     * 저장되었고 아직 발행하지 않았다.
     */
    PENDING,

    /**
     * 브로커에 발행했다.
     */
    PUBLISHED,

    /**
     * 발행에 실패했다. 다시 시도할지는 서비스가 attempts 로 정한다.
     */
    FAILED

}
