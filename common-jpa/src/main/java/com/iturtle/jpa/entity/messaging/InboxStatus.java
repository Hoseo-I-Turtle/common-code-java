package com.iturtle.jpa.entity.messaging;

/**
 * Inbox 메시지의 처리 진행 상황.
 */
public enum InboxStatus {

    /**
     * 받아서 저장했고 아직 처리하지 않았다.
     */
    RECEIVED,

    /**
     * 처리했다.
     */
    PROCESSED,

    /**
     * 처리에 실패했다. 다시 시도할지는 서비스가 attempts 로 정한다.
     */
    FAILED
}
