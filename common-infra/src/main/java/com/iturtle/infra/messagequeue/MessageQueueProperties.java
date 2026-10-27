package com.iturtle.infra.messagequeue;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 메시지 큐 설정. 접속(리전, 키, 주소)은 Spring Cloud AWS(spring.cloud.aws.*)가 맡는다.
 * <pre>
 * iturtle:
 *   infra:
 *     message-queue:
 *       enabled: true
 *       type: sqs
 *       max-concurrent-messages: 10      # 리스너마다 동시에 처리하는 메시지 수
 *       queues:                          # 별칭: 실제 큐 이름 (환경마다 다르게)
 *         orders:
 *           name: prod-orders
 *           dead-letter-name: prod-orders-dlq   # 선택. DLQ 를 읽을 때만
 *       dead-letter:
 *         hold-timeout: 30s              # 엿본 메시지를 되돌리지 못했을 때 다시 보이기까지의 시간
 * </pre>
 * 리스너는 &#64;SqsListener(value = "${iturtle.infra.message-queue.queues.orders.name}"), 발행 / DLQ 읽기는 MessageQueues 로 이름을 얻는다.
 * 적은 큐와 DLQ 가 실제로 있는지 기동할 때 확인한다.
 * <p>
 * 큐와 DLQ, redrive 정책은 앱이 만들지 않는다 (AWS 콘솔 / IaC, 로컬은 ElasticMQ 설정). 실패한 메시지를 DLQ 로 옮기는 것도
 * redrive 정책(maxReceiveCount)대로 SQS 가 한다. 다시 받으면 안 되는 큐는 maxReceiveCount 를 1 로 둔다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "iturtle.infra.message-queue")
public class MessageQueueProperties {

    /** true 일 때만 등록한다 */
    private boolean enabled = false;

    private MessageQueueType type;

    private int maxConcurrentMessages = 10;

    /** 별칭별 큐 */
    private Map<String, Queue> queues = new LinkedHashMap<>();

    /** DLQ 읽기 (SqsDeadLetterQueueClient) */
    private DeadLetter deadLetter = new DeadLetter();

    @Getter
    @Setter
    public static class Queue {

        /** 실제 큐 이름 */
        private String name;

        /** DLQ 이름. DLQ 를 읽을 때만 적는다 (DLQ 로 옮기는 것은 redrive 정책대로 SQS 가 한다) */
        private String deadLetterName;
    }

    @Getter
    @Setter
    public static class DeadLetter {

        /** 엿보기로 받은 메시지는 곧바로 되돌린다. 되돌리지 못했을 때(앱이 죽는 등) 이 시간이 지나면 SQS 가 다시 보이게 한다 */
        private Duration holdTimeout = Duration.ofSeconds(30);
    }
}
