package com.iturtle.infra.messagequeue.sqs;

import com.iturtle.infra.messagequeue.MessageQueuePublishException;
import com.iturtle.infra.messagequeue.MessageQueuePublisher;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.messaging.MessagingException;
import software.amazon.awssdk.core.exception.SdkException;

import java.util.HashMap;
import java.util.Map;

/**
 * SQS 구현. Spring Cloud AWS 의 SqsTemplate 으로 보낸다 (payload 는 JSON, 헤더는 메시지 속성).
 */
public class SqsMessageQueuePublisher implements MessageQueuePublisher {

    private final SqsTemplate sqsTemplate;

    public SqsMessageQueuePublisher(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    @Override
    public void send(String queue, Object payload) {
        send(queue, payload, Map.of());
    }

    @Override
    public void send(String queue, Object payload, Map<String, String> headers) {
        Map<String, Object> messageHeaders = new HashMap<>(headers);
        try {
            sqsTemplate.send(to ->
                    to.queue(queue)
                    .payload(payload)
                    .headers(messageHeaders)
            );
        } catch (MessagingException | SdkException e) {
            throw new MessageQueuePublishException(queue + " 큐에 메시지를 올리지 못했다", e);
        }
    }

}
