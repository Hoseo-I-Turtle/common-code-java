package com.iturtle.infra.messagequeue.sqs;

/**
 * 공통 설정(없는 큐는 기동 실패, 동시 처리 수)이 걸린 리스너 팩토리 빈의 이름. 실패한 메시지는 redrive 정책대로 SQS 가 DLQ 로 옮긴다.
 * <pre>
 * &#64;SqsListener(value = "${app.sqs.order-queue}", factory = SqsListenerFactory.NAME)
 * public void onOrder(OrderMessage message) { ... }
 * </pre>
 * factory 를 적지 않으면 Spring Cloud AWS 기본 팩토리가 쓰여, 없는 큐를 조용히 만들고 동시 처리 수 설정이 걸리지 않는다.
 */
public final class SqsListenerFactory {

    public static final String NAME = "iturtleSqsListenerContainerFactory";

    private SqsListenerFactory() {}
}
