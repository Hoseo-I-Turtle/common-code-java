package com.iturtle.infra.autoconfigure;

import com.iturtle.core.exception.InvalidConfigurationException;
import com.iturtle.infra.messagequeue.MessageQueueProperties;
import com.iturtle.infra.messagequeue.MessageQueuePublisher;
import com.iturtle.infra.messagequeue.MessageQueues;
import com.iturtle.infra.messagequeue.sqs.SqsDeadLetterQueueClient;
import com.iturtle.infra.messagequeue.sqs.SqsListenerFactory;
import com.iturtle.infra.messagequeue.sqs.SqsMessageQueuePublisher;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.QueueNotFoundStrategy;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

/**
 * iturtle.infra.message-queue.enabled=true, type=sqs 이고 Spring Cloud AWS SQS 가 있을 때 공통 SQS 구성을 등록한다.
 * <ul>
 *     <li>SqsAsyncClient / SqsTemplate 은 Spring Cloud AWS(spring.cloud.aws.*)가 만든다. 이 설정은 클라이언트를 만들지 않는다</li>
 *     <li>SqsListenerFactory.NAME 리스너 팩토리: 없는 큐는 기동 실패, 동시 처리 수 = 한 번에 받는 수</li>
 *     <li>MessageQueuePublisher(SqsMessageQueuePublisher), SqsDeadLetterQueueClient(DLQ 읽기)</li>
 *     <li>MessageQueues: 별칭으로 큐 이름을 얻는다. 적은 큐와 DLQ 가 없으면 기동 실패</li>
 * </ul>
 * 실패한 메시지는 지우지 않으므로(기본 ON_SUCCESS) 가시성 시간 뒤 다시 오고, redrive 정책의 횟수를 다 쓰면 SQS 가 DLQ 로 옮긴다.
 * Spring Cloud AWS 의 빈을 보도록 그 자동 설정 다음에 실행한다. 서비스가 같은 타입 / 이름의 빈을 등록하면 그것을 쓴다.
 * 발행 쪽에도 없는 큐를 만들지 않게 하려면 spring.cloud.aws.sqs.queue-not-found-strategy=fail 을 준다.
 */
@AutoConfiguration(afterName = "io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration")
@ConditionalOnClass(name = "io.awspring.cloud.sqs.operations.SqsTemplate")
@ConditionalOnProperty(prefix = "iturtle.infra.message-queue", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MessageQueueProperties.class)
public class SqsMessageQueueAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "iturtle.infra.message-queue", name = "type", havingValue = "sqs")
    @ConditionalOnBean(SqsAsyncClient.class)
    static class SqsConfiguration {

        /**
         * 없는 큐를 만들어 버리면 redrive 도 없는 큐에 메시지가 쌓이므로 기동에서 실패시킨다 (라이브러리 기본은 만든다).
         */
        @Bean(SqsListenerFactory.NAME)
        @ConditionalOnMissingBean(name = SqsListenerFactory.NAME)
        SqsMessageListenerContainerFactory<Object> iturtleSqsListenerContainerFactory(
                SqsAsyncClient client, MessageQueueProperties properties) {
            int maxConcurrentMessages = properties.getMaxConcurrentMessages();
            if (maxConcurrentMessages < 1) {
                throw new InvalidConfigurationException("iturtle.infra.message-queue.max-concurrent-messages 는 1 이상이어야 한다");
            }
            return SqsMessageListenerContainerFactory.builder()
                    .sqsAsyncClient(client)
                    // 한 번에 받아 오는 수가 동시에 처리하는 수를 넘을 수 없다(라이브러리 제약). 받아 놓고 기다리게 두지 않으려고 같게 둔다
                    .configure(options -> options
                            .queueNotFoundStrategy(QueueNotFoundStrategy.FAIL)
                            .maxConcurrentMessages(maxConcurrentMessages)
                            .maxMessagesPerPoll(maxConcurrentMessages))
                    .build();
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(SqsTemplate.class)
        MessageQueuePublisher sqsMessageQueuePublisher(SqsTemplate sqsTemplate) {
            return new SqsMessageQueuePublisher(sqsTemplate);
        }

        @Bean
        @ConditionalOnMissingBean
        SqsDeadLetterQueueClient sqsDeadLetterQueueClient(SqsAsyncClient client, MessageQueueProperties properties) {
            MessageQueueProperties.DeadLetter deadLetter = properties.getDeadLetter();
            // SQS 가시성 시간은 1초 ~ 12시간이다
            if (deadLetter.getHoldTimeout().toSeconds() < 1 || deadLetter.getHoldTimeout().compareTo(Duration.ofHours(12)) > 0) {
                throw new InvalidConfigurationException(
                        "iturtle.infra.message-queue.dead-letter.hold-timeout 은 1초 ~ 12시간이어야 한다");
            }
            return new SqsDeadLetterQueueClient(client, deadLetter.getHoldTimeout());
        }

        /**
         * 적은 큐와 DLQ 가 실제로 있는지 기동할 때 확인한다. 발행만 하는 큐도 이름 오타가 여기서 드러난다.
         */
        @Bean
        @ConditionalOnMissingBean
        MessageQueues messageQueues(MessageQueueProperties properties, SqsAsyncClient client) {
            MessageQueues queues = new MessageQueues(properties);
            List<String> missing = new ArrayList<>();
            for (String name : queues.allNames()) {
                try {
                    client.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build()).join();
                } catch (CompletionException e) {
                    if (!(e.getCause() instanceof QueueDoesNotExistException)) {
                        throw new IllegalStateException("SQS 에서 큐 " + name + " 를 확인하지 못했다", e);
                    }
                    missing.add(name);
                }
            }
            if (!missing.isEmpty()) {
                throw new InvalidConfigurationException("iturtle.infra.message-queue.queues 에 적은 큐가 없다: " + missing
                        + ". 큐와 DLQ 는 콘솔 / IaC 에서 만든다");
            }
            return queues;
        }
    }
}
