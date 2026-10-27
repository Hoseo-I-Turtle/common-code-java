package com.iturtle.infra.messagequeue;

import com.iturtle.core.exception.InvalidConfigurationException;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 별칭으로 실제 큐 이름을 얻는다 (iturtle.infra.message-queue.queues). 환경마다 다른 이름을 코드에 적지 않는다.
 * <pre>
 * messageQueuePublisher.send(messageQueues.nameOf("orders"), message);
 * sqsDeadLetterQueueClient.peek(messageQueues.deadLetterNameOf("orders"), 10);
 * </pre>
 */
public class MessageQueues {

    private final Map<String, MessageQueueProperties.Queue> queues;

    public MessageQueues(MessageQueueProperties properties) {
        this.queues = Collections.unmodifiableMap(new LinkedHashMap<>(properties.getQueues()));
        queues.forEach((alias, queue) -> {
            if (!StringUtils.hasText(queue.getName())) {
                throw new InvalidConfigurationException("iturtle.infra.message-queue.queues." + alias + ".name 이 필요하다");
            }
        });
    }

    /**
     * 설정하지 않은 별칭이면 IllegalArgumentException.
     */
    public String nameOf(String alias) {
        return queueOf(alias).getName();
    }

    /**
     * dead-letter-name 을 적지 않았으면 IllegalStateException.
     */
    public String deadLetterNameOf(String alias) {
        String deadLetterName = queueOf(alias).getDeadLetterName();
        if (!StringUtils.hasText(deadLetterName)) {
            throw new IllegalStateException(
                    "iturtle.infra.message-queue.queues." + alias + ".dead-letter-name 이 설정되지 않았다");
        }
        return deadLetterName;
    }

    public Set<String> aliases() {
        return queues.keySet();
    }

    /**
     * 설정한 모든 큐와 DLQ 의 실제 이름.
     */
    public Set<String> allNames() {
        return queues.values().stream()
                .flatMap(queue -> StringUtils.hasText(queue.getDeadLetterName())
                        ? Stream.of(queue.getName(), queue.getDeadLetterName())
                        : Stream.of(queue.getName()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private MessageQueueProperties.Queue queueOf(String alias) {
        MessageQueueProperties.Queue queue = queues.get(alias);
        if (queue == null) {
            throw new IllegalArgumentException(
                    "iturtle.infra.message-queue.queues." + alias + " 가 설정되지 않았다 (설정된 큐: " + queues.keySet() + ")");
        }
        return queue;
    }
}
