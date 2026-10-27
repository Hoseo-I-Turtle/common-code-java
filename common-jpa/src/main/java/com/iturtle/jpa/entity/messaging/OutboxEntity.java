package com.iturtle.jpa.entity.messaging;

import com.iturtle.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Transactional outbox. 도메인 변경과 같은 트랜잭션에서 저장하고, 릴레이가 PENDING 행을 읽어 브로커로 발행한다.
 * <p>
 * 이벤트 종류 EVENT_TYPE 은 enum 이나 String 이며, 어느 쪽이든 문자열로 저장한다 (enum 은 이름). 대부분은 eventType 과 payload 로
 * 충분하다. 메시지 키(같은 aggregate 순서 보장)나 Debezium Outbox Event Router 처럼 aggregate 를 컬럼으로 읽어야 하면
 * PolymorphicOutboxEntity 를 상속한다. 재시도 횟수 / 간격, 릴레이 폴링과 잠금, 발행된 행 정리는 서비스가 정한다.
 * <pre>
 * public enum OrderEvent { CREATED, PAID, CANCELED }
 *
 * &#64;Entity
 * &#64;Table(indexes = @Index(columnList = "status, created_at"))
 * public class OrderOutbox extends OutboxEntity&lt;OrderEvent&gt; {
 *     &#64;Id &#64;GeneratedValue
 *     private Long id;
 * }
 * </pre>
 */
@Getter
@MappedSuperclass
public abstract class OutboxEntity<EVENT_TYPE> extends CreatedAtEntity {

    /**
     * 컨슈머가 중복 제거 키(InboxEntity 의 messageId)로 쓴다. 서비스마다 키 형식이 다르므로 서비스가 정해 넣는다.
     */
    @Column(nullable = false, updatable = false, unique = true)
    private String eventId;

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

    protected OutboxEntity() {
    }

    protected OutboxEntity(String eventId, EVENT_TYPE eventType, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public void markPublished() {
        attempts++;
        status = OutboxStatus.PUBLISHED;
        publishedAt = Instant.now();
    }

    public void markFailed() {
        attempts++;
        status = OutboxStatus.FAILED;
    }

}
