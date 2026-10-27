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
 * Inbox (idempotent consumer). 받은 메시지를 처리하기 전에 저장한다. messageId 가 unique 이므로 같은 메시지를 다시 받으면
 * 저장이 실패하고, 이것으로 중복 처리를 막는다.
 * <p>
 * 이벤트 종류 EVENT_TYPE 은 enum 이나 String 이며, 어느 쪽이든 문자열로 저장한다 (enum 은 이름). 모르는 종류를 보낼 수 있는 외부 시스템
 * 메시지는 String 으로 받는다. 재시도 횟수 / 간격, 처리된 행 정리는 서비스가 정한다.
 * <pre>
 * &#64;Entity
 * &#64;Table(indexes = @Index(columnList = "status, created_at"))
 * public class PaymentInbox extends InboxEntity&lt;PaymentEvent&gt; {
 *     &#64;Id &#64;GeneratedValue
 *     private Long id;
 * }
 * </pre>
 */
@Getter
@MappedSuperclass
public abstract class InboxEntity<EVENT_TYPE> extends CreatedAtEntity {

    /**
     * 보낸 쪽이 정한 메시지 식별자 (OutboxEntity 의 eventId 등). 외부 시스템 메시지도 받도록 문자열로 둔다.
     */
    @Column(nullable = false, updatable = false, unique = true)
    private String messageId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false)
    private EVENT_TYPE eventType;

    /**
     * 직렬화된 메시지 본문 (예: JSON).
     */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboxStatus status;

    /**
     * 처리 시도 횟수 (성공 / 실패 모두).
     */
    @Column(nullable = false)
    private int attempts;

    private Instant processedAt;

    protected InboxEntity() {
    }

    protected InboxEntity(String messageId, EVENT_TYPE eventType, String payload) {
        this.messageId = messageId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = InboxStatus.RECEIVED;
    }

    public void markProcessed() {
        attempts++;
        status = InboxStatus.PROCESSED;
        processedAt = Instant.now();
    }

    public void markFailed() {
        attempts++;
        status = InboxStatus.FAILED;
    }
}
