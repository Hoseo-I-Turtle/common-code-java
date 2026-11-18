package com.iturtle.jpa.entity.messaging;

import com.iturtle.core.retry.BackoffPolicy;
import com.iturtle.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Optional;

/**
 * Transactional outbox. 도메인 변경과 같은 트랜잭션에서 저장하고, 릴레이가 PENDING 행을 읽어 브로커로 발행한다.
 * <p>
 * EVENT_ID 는 eventId 의 타입이다 (UUID, String 등. UUID 는 DB 의 uuid / binary 로 저장된다).
 * 이벤트 종류 EVENT_TYPE 은 enum 이나 String 이며, 어느 쪽이든 문자열로 저장한다 (enum 은 이름). 대부분은 eventType 과 payload 로
 * 충분하다. 메시지 키(같은 aggregate 순서 보장)나 Debezium Outbox Event Router 처럼 aggregate 를 컬럼으로 읽어야 하면
 * PolymorphicOutboxEntity 를 상속한다. 릴레이 폴링과 잠금, 발행된 행 정리는 서비스가 정한다.
 * <p>
 * 재시도: {@link #markFailed(String, BackoffPolicy)} 는 시도 횟수가 남으면 PENDING 으로 두고 다음 시도 시각
 * ({@code next_run_at})을 정한다. 릴레이는 {@code status = PENDING and (next_run_at is null or next_run_at <= now)} 를 읽는다.
 * 횟수를 다 쓰면 FAILED 다. 인자 없는 {@link #markFailed()} 는 바로 FAILED 로 끝낸다 (재시도하지 않는 릴레이).
 * <pre>
 * public enum OrderEvent { CREATED, PAID, CANCELED }
 *
 * &#64;Entity
 * &#64;Table(indexes = @Index(columnList = "status, next_run_at"))
 * public class OrderOutbox extends OutboxEntity&lt;OrderEvent, UUID&gt; {
 *     &#64;Id &#64;GeneratedValue
 *     private Long id;
 * }
 * </pre>
 */
@Getter
@MappedSuperclass
public abstract class OutboxEntity<EVENT_TYPE, EVENT_ID> extends CreatedAtEntity {

    /**
     * 컨슈머가 중복 제거 키(InboxEntity 의 messageId)로 쓴다. 서비스가 정해 넣는다.
     */
    @Column(nullable = false, updatable = false, unique = true)
    private EVENT_ID eventId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false)
    private EVENT_TYPE eventType;

    /**
     * 직렬화된 이벤트 본문 (예: JSON).
     */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    /**
     * 발행 시도 횟수 (성공 / 실패 모두).
     */
    @Column(nullable = false)
    private int attempts;

    private Instant publishedAt;

    /**
     * 다음 발행 시도 시각. 처음 저장할 때는 null (바로 발행). 재시도를 기다리면 그 시각.
     */
    private Instant nextRunAt;

    /**
     * 마지막 실패 사유 (최대 {@value #LAST_ERROR_MAX_LENGTH}자).
     */
    @Column(length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    public static final int LAST_ERROR_MAX_LENGTH = 1000;

    protected OutboxEntity() {
    }

    protected OutboxEntity(EVENT_ID eventId, EVENT_TYPE eventType, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public void markPublished() {
        attempts++;
        status = OutboxStatus.PUBLISHED;
        publishedAt = Instant.now();
        nextRunAt = null;
    }

    /**
     * 재시도하지 않고 실패로 끝낸다.
     */
    public void markFailed() {
        attempts++;
        status = OutboxStatus.FAILED;
        nextRunAt = null;
    }

    public void markFailed(String error, BackoffPolicy policy) {
        markFailed(error, policy, Instant.now());
    }

    /**
     * 실패를 기록하고, 시도 횟수가 남았으면 PENDING 으로 두어 다음 시도 시각을 정한다. 다 썼으면 FAILED.
     * 오류 메시지는 {@value #LAST_ERROR_MAX_LENGTH}자로 자른다.
     */
    public void markFailed(String error, BackoffPolicy policy, Instant now) {
        attempts++;
        lastError = error == null || error.length() <= LAST_ERROR_MAX_LENGTH ? error : error.substring(0, LAST_ERROR_MAX_LENGTH);
        Optional<Instant> next = policy.nextAttemptAt(attempts, now);
        if (next.isPresent()) {
            status = OutboxStatus.PENDING;
            nextRunAt = next.get();
        } else {
            status = OutboxStatus.FAILED;
            nextRunAt = null;
        }
    }

    /**
     * 릴레이가 now 에 발행할 차례인지.
     */
    public boolean isDue(Instant now) {
        return status == OutboxStatus.PENDING && (nextRunAt == null || !nextRunAt.isAfter(now));
    }

}
