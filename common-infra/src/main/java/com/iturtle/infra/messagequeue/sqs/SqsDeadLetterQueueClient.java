package com.iturtle.infra.messagequeue.sqs;

import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * DLQ 를 읽는다 (관리 화면용). 건수 보기, 엿보기. 메시지를 DLQ 로 옮기는 것은 redrive 정책대로 SQS 가 하고,
 * 원래 큐로 되돌리기 / 버리기는 콘솔(Start DLQ redrive, Purge)에서 한다.
 * <p>
 * SQS 에는 정렬 / 건너뛰기가 없어 페이지 단위로 읽을 수 없다. 엿보기는 한 번 받기(최대 10건)만 하고, 받는 메시지는 매번 일부만 뽑힌다.
 * 실패는 SDK 예외(CompletionException)로 그대로 나간다.
 */
public class SqsDeadLetterQueueClient {

    /** 한 번 받기의 한도 (SQS API 고정값) */
    private static final int RECEIVE_MAX = 10;

    private final SqsAsyncClient sqsAsyncClient;

    private final Duration holdTimeout;

    public SqsDeadLetterQueueClient(SqsAsyncClient sqsAsyncClient, Duration holdTimeout) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.holdTimeout = holdTimeout;
    }

    /**
     * 근사 건수 두 가지. 보이는 것(ApproximateNumberOfMessages)과 누가 받아 숨겨진 것(ApproximateNumberOfMessagesNotVisible).
     * 화면의 전체 건수로 쓴다.
     */
    public Map<QueueAttributeName, String> countsOf(String queueName) {
        String queueUrl = urlOf(queueName);
        return sqsAsyncClient.getQueueAttributes(r -> r.queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE))
                .join()
                .attributes();
    }

    /**
     * 지우지 않고 최대 max 건(SQS 한도 10)을 보낸 시각 순으로 본다.
     * <p>
     * SQS 는 받기 말고는 내용을 볼 방법이 없다. 받으면 메시지가 숨겨지므로 곧바로 가시성을 0 으로 돌려 다시 보이게 한다.
     * 되돌리지 못하면(앱이 죽는 등) hold-timeout 이 지나 다시 보인다. 받을 때마다 수신 횟수가 오른다.
     */
    public List<Message> peek(String queueName, int max) {
        String queueUrl = urlOf(queueName);
        List<Message> messages = new ArrayList<>(receive(queueUrl, Math.min(max, RECEIVE_MAX)));
        release(queueUrl, messages);
        messages.sort(Comparator.comparingLong(SqsDeadLetterQueueClient::sentAt));
        return messages;
    }

    private List<Message> receive(String queueUrl, int max) {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(max)
                .visibilityTimeout((int) holdTimeout.toSeconds())
                .messageSystemAttributeNames(MessageSystemAttributeName.SENT_TIMESTAMP,
                        MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                .messageAttributeNames("All")
                .build();
        return sqsAsyncClient.receiveMessage(request).join().messages();
    }

    /**
     * 받아 숨겨진 메시지를 곧바로 다시 보이게 한다.
     */
    private void release(String queueUrl, List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }
        List<ChangeMessageVisibilityBatchRequestEntry> entries = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            entries.add(ChangeMessageVisibilityBatchRequestEntry.builder()
                    .id(String.valueOf(i))
                    .receiptHandle(messages.get(i).receiptHandle())
                    .visibilityTimeout(0)
                    .build());
        }
        sqsAsyncClient.changeMessageVisibilityBatch(r -> r.queueUrl(queueUrl).entries(entries)).join();
    }

    private static long sentAt(Message message) {
        String sentTimestamp = message.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP);
        return sentTimestamp == null ? Long.MAX_VALUE : Long.parseLong(sentTimestamp);
    }

    private String urlOf(String queueName) {
        return sqsAsyncClient.getQueueUrl(r -> r.queueName(queueName)).join().queueUrl();
    }
}
