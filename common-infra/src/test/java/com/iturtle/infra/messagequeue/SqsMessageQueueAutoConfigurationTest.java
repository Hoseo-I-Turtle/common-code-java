package com.iturtle.infra.messagequeue;

import com.iturtle.core.exception.InvalidConfigurationException;
import com.iturtle.infra.autoconfigure.SqsMessageQueueAutoConfiguration;
import com.iturtle.infra.messagequeue.sqs.SqsDeadLetterQueueClient;
import com.iturtle.infra.messagequeue.sqs.SqsListenerFactory;
import com.iturtle.infra.messagequeue.sqs.SqsMessageQueuePublisher;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SQS 메시지 큐 자동 설정의 등록 조건과 설정 검사를 검증한다 (실제 SQS 없이).
 */
class SqsMessageQueueAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SqsMessageQueueAutoConfiguration.class))
            .withBean(SqsAsyncClient.class, () -> mock(SqsAsyncClient.class))
            .withBean(SqsTemplate.class, () -> mock(SqsTemplate.class));

    private static final String[] VALID = {
            "iturtle.infra.message-queue.enabled=true",
            "iturtle.infra.message-queue.type=sqs"};

    @Test
    void notRegisteredByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(MessageQueuePublisher.class)
                .doesNotHaveBean(SqsListenerFactory.NAME));
    }

    @Test
    void registeredWhenEnabled() {
        runner.withPropertyValues(VALID)
                .run(context -> {
                    assertThat(context).getBean(MessageQueuePublisher.class).isInstanceOf(SqsMessageQueuePublisher.class);
                    assertThat(context).hasSingleBean(SqsDeadLetterQueueClient.class);
                    assertThat(context.getBean(SqsListenerFactory.NAME)).isInstanceOf(SqsMessageListenerContainerFactory.class);
                });
    }

    @Test
    void notRegisteredWithoutSpringCloudAws() {
        runner.withClassLoader(new FilteredClassLoader(SqsTemplate.class))
                .withPropertyValues(VALID)
                .run(context -> assertThat(context).doesNotHaveBean(MessageQueuePublisher.class));
    }

    @Test
    void notRegisteredWithoutClient() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SqsMessageQueueAutoConfiguration.class))
                .withPropertyValues(VALID)
                .run(context -> assertThat(context).doesNotHaveBean(SqsDeadLetterQueueClient.class)
                        .doesNotHaveBean(SqsListenerFactory.NAME));
    }

    @Test
    void failsWithInvalidConcurrency() {
        runner.withPropertyValues(VALID)
                .withPropertyValues("iturtle.infra.message-queue.max-concurrent-messages=0")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(InvalidConfigurationException.class)
                        .hasMessageContaining("max-concurrent-messages"));
    }

    @Test
    void queuesResolvedByAlias() {
        SqsAsyncClient client = mock(SqsAsyncClient.class);
        when(client.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl("url").build()));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SqsMessageQueueAutoConfiguration.class))
                .withBean(SqsAsyncClient.class, () -> client)
                .withPropertyValues(VALID)
                .withPropertyValues(
                        "iturtle.infra.message-queue.queues.orders.name=prod-orders",
                        "iturtle.infra.message-queue.queues.orders.dead-letter-name=prod-orders-dlq",
                        "iturtle.infra.message-queue.queues.push.name=prod-push")
                .run(context -> {
                    MessageQueues queues = context.getBean(MessageQueues.class);
                    assertThat(queues.nameOf("orders")).isEqualTo("prod-orders");
                    assertThat(queues.deadLetterNameOf("orders")).isEqualTo("prod-orders-dlq");
                    assertThat(queues.allNames()).containsExactly("prod-orders", "prod-orders-dlq", "prod-push");
                    assertThatThrownBy(() -> queues.deadLetterNameOf("push")).isInstanceOf(IllegalStateException.class);
                    assertThatThrownBy(() -> queues.nameOf("unknown"))
                            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("orders");
                });
    }

    @Test
    void failsWhenQueueMissing() {
        SqsAsyncClient client = mock(SqsAsyncClient.class);
        when(client.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(QueueDoesNotExistException.builder().message("missing").build()));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SqsMessageQueueAutoConfiguration.class))
                .withBean(SqsAsyncClient.class, () -> client)
                .withPropertyValues(VALID)
                .withPropertyValues("iturtle.infra.message-queue.queues.orders.name=prod-ordres")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(InvalidConfigurationException.class)
                        .hasMessageContaining("prod-ordres"));
    }

    @Test
    void failsWithoutQueueName() {
        runner.withPropertyValues(VALID)
                .withPropertyValues("iturtle.infra.message-queue.queues.orders.dead-letter-name=prod-orders-dlq")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(InvalidConfigurationException.class)
                        .hasMessageContaining("queues.orders.name"));
    }

    @Test
    void failsWithInvalidHoldTimeout() {
        runner.withPropertyValues(VALID)
                .withPropertyValues("iturtle.infra.message-queue.dead-letter.hold-timeout=13h")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("dead-letter.hold-timeout"));
    }

    @Test
    void servicePublisherUsed() {
        MessageQueuePublisher custom = mock(MessageQueuePublisher.class);
        runner.withBean(MessageQueuePublisher.class, () -> custom)
                .withPropertyValues(VALID)
                .run(context -> assertThat(context).getBean(MessageQueuePublisher.class).isSameAs(custom));
    }
}
