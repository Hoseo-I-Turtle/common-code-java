package com.iturtle.infra.messagequeue;

import java.util.Map;

/**
 * 메시지 큐에 메시지를 올린다. 구현은 인프라별로 둔다.
 * <p>
 * payload 는 구현이 직렬화한다 (SQS 는 JSON). 헤더는 메시지 속성으로 실린다 (추적 번호 등). 실패하면 MessageQueuePublishException.
 * 도메인 변경과 함께 반드시 나가야 하는 이벤트는 직접 올리지 말고 OutboxEntity(common-jpa)에 저장한 뒤 릴레이가 이것으로 올린다.
 */
public interface MessageQueuePublisher {

    void send(String queue, Object payload);

    void send(String queue, Object payload, Map<String, String> headers);

}
