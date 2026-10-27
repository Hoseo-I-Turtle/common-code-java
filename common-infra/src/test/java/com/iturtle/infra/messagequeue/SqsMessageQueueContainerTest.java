package com.iturtle.infra.messagequeue;

import com.iturtle.infra.messagequeue.sqs.SqsDeadLetterQueueClient;
import com.iturtle.infra.messagequeue.sqs.SqsListenerFactory;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 실제 SQS 호환 서버(ElasticMQ)에서 발행, 수신, SQS redrive 에 의한 DLQ 이동, DLQ 읽기를 Spring Cloud AWS 자동 설정 그대로 검증한다.
 * Docker 가 없으면 건너뛴다.
 * <ul>
 *     <li>orders: redrive maxReceiveCount=2, 가시성 2초 (재시도가 테스트 안에 끝나도록 짧게)</li>
 *     <li>push: redrive maxReceiveCount=1 (다시 받지 않는 큐)</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SqsMessageQueueContainerTest.TestApplication.class)
// 컨테이너보다 먼저 컨텍스트를 닫는다. 안 그러면 컨테이너가 내려간 뒤에도 리스너가 폴링하며 연결 오류를 쏟는다
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SqsMessageQueueContainerTest {

    @Container
    static final GenericContainer<?> SQS = new GenericContainer<>(DockerImageName.parse("softwaremill/elasticmq-native:1.7.1"))
            .withExposedPorts(9324);

    private static final Duration WAIT = Duration.ofSeconds(30);

    private static SqsClient admin;

    @Autowired
    private MessageQueuePublisher publisher;

    @Autowired
    private SqsDeadLetterQueueClient deadLetterQueueClient;

    @Autowired
    private Listeners listeners;

    @Autowired
    private MessageQueues queues;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.sqs.endpoint", SqsMessageQueueContainerTest::endpoint);
        registry.add("spring.cloud.aws.region.static", () -> "us-east-1");
        registry.add("spring.cloud.aws.credentials.access-key", () -> "ak");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "sk");
        registry.add("iturtle.infra.message-queue.enabled", () -> "true");
        registry.add("iturtle.infra.message-queue.type", () -> "sqs");
        registry.add("iturtle.infra.message-queue.queues.orders.name", () -> "orders");
        registry.add("iturtle.infra.message-queue.queues.orders.dead-letter-name", () -> "orders-dlq");
        registry.add("iturtle.infra.message-queue.queues.push.name", () -> "push");
        registry.add("iturtle.infra.message-queue.queues.push.dead-letter-name", () -> "push-dlq");
    }

    /**
     * 큐, DLQ, redrive 정책은 콘솔 / IaC 가 만드는 것을 흉내 낸다. 리스너가 기동할 때 큐가 있어야 하므로 컨텍스트보다 먼저 만든다.
     */
    @BeforeAll
    static void createQueues() {
        admin = SqsClient.builder()
                .endpointOverride(URI.create(endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
                .build();
        createWithDeadLetter("orders", 2);
        createWithDeadLetter("push", 1);
    }

    @AfterAll
    static void closeAdmin() {
        admin.close();
    }

    @BeforeEach
    void reset() {
        listeners.clear();
        for (String queue : List.of("orders-dlq", "push-dlq")) {
            String url = admin.getQueueUrl(r -> r.queueName(queue)).queueUrl();
            admin.purgeQueue(r -> r.queueUrl(url));
        }
    }

    @Test
    void publishedMessageReceivedWithHeader() {
        publisher.send(queues.nameOf("orders"), "ok-1", Map.of("traceId", "trace-1"));

        await().atMost(WAIT).untilAsserted(() -> assertThat(listeners.received).contains("ok-1|trace-1"));
    }

    @Test
    void failedMessageRetriedThenMovedToDeadLetterBySqs() {
        publisher.send(queues.nameOf("orders"), "fail-1");

        // 실패한 메시지는 지워지지 않아 가시성 시간 뒤 다시 오고, maxReceiveCount=2 번 받은 뒤 SQS 가 DLQ 로 옮긴다
        await().atMost(WAIT).untilAsserted(() -> assertThat(visibleCount(queues.deadLetterNameOf("orders"))).isEqualTo(1));
        assertThat(listeners.attempts("fail-1")).isEqualTo(2);
    }

    @Test
    void maxReceiveCountOneNotRetried() {
        publisher.send(queues.nameOf("push"), "fail-push");

        await().atMost(WAIT).untilAsserted(() -> assertThat(visibleCount(queues.deadLetterNameOf("push"))).isEqualTo(1));
        assertThat(listeners.attempts("fail-push")).isEqualTo(1);
    }

    @Test
    void peekKeepsMessages() {
        String deadLetter = queues.deadLetterNameOf("orders");
        for (int i = 0; i < 3; i++) {
            publisher.send(deadLetter, "dead-" + i, Map.of("traceId", "trace-" + i));
        }
        await().atMost(WAIT).untilAsserted(() -> assertThat(visibleCount(deadLetter)).isEqualTo(3));

        List<Message> peeked = deadLetterQueueClient.peek(deadLetter, 10);

        assertThat(peeked).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(peeked).extracting(m -> Long.parseLong(m.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP)))
                .isSorted();
        assertThat(peeked.getFirst().messageAttributes()).containsKey("traceId");
        // 지우지 않고 곧바로 되돌린다
        assertThat(visibleCount(deadLetter)).isEqualTo(3);
        assertThat(deadLetterQueueClient.peek(deadLetter, 10)).isNotEmpty();
    }

    private int visibleCount(String queue) {
        return Integer.parseInt(deadLetterQueueClient.countsOf(queue).get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }

    private static void createWithDeadLetter(String queue, int maxReceiveCount) {
        String deadLetterUrl = admin.createQueue(r -> r.queueName(queue + "-dlq")).queueUrl();
        String deadLetterArn = admin.getQueueAttributes(r -> r.queueUrl(deadLetterUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
        admin.createQueue(r -> r.queueName(queue).attributes(Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                QueueAttributeName.REDRIVE_POLICY,
                "{\"deadLetterTargetArn\":\"" + deadLetterArn + "\",\"maxReceiveCount\":\"" + maxReceiveCount + "\"}")));
    }

    private static String endpoint() {
        return "http://" + SQS.getHost() + ":" + SQS.getMappedPort(9324);
    }

    /**
     * payload 접두사로 동작을 정한다. ok: 처리, fail: 예외.
     */
    static class Listeners {

        final List<String> received = new CopyOnWriteArrayList<>();
        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        @SqsListener(value = "${iturtle.infra.message-queue.queues.orders.name}", factory = SqsListenerFactory.NAME)
        void onOrder(String payload, @Header(name = "traceId", required = false) String traceId) {
            handle(payload, traceId);
        }

        @SqsListener(value = "${iturtle.infra.message-queue.queues.push.name}", factory = SqsListenerFactory.NAME)
        void onPush(String payload) {
            handle(payload, null);
        }

        private void handle(String payload, String traceId) {
            attempts.computeIfAbsent(payload, ignored -> new AtomicInteger()).incrementAndGet();
            if (payload.startsWith("fail")) {
                throw new IllegalStateException("처리 실패 payload=" + payload);
            }
            received.add(payload + "|" + traceId);
        }

        int attempts(String payload) {
            AtomicInteger count = attempts.get(payload);
            return count == null ? 0 : count.get();
        }

        void clear() {
            received.clear();
            attempts.clear();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(Listeners.class)
    static class TestApplication {
    }
}
