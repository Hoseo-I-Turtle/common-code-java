package com.iturtle.jpa.entity.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 이벤트가 나온 aggregate 를 컬럼으로 기록하는 outbox (polymorphic association).
 * <p>
 * aggregate 를 종류(aggregateType) + 식별자(aggregateId)로 가리킨다. AGGREGATE_TYPE 은 EVENT_TYPE 과 같이 enum 이나
 * String 이며 문자열로 저장한다. payload 를 열지 않고 aggregate 를 읽어야 할 때 쓴다: 메시지 키로 같은 aggregate 의 순서를 보장하거나,
 * Debezium Outbox Event Router 가 aggregatetype / aggregateid 컬럼으로 토픽과 키를 정할 때. 사용법은 OutboxEntity 참고.
 */
@Getter
@MappedSuperclass
public abstract class PolymorphicOutboxEntity<EVENT_TYPE, AGGREGATE_TYPE, EVENT_ID>
        extends OutboxEntity<EVENT_TYPE, EVENT_ID> {

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false)
    private AGGREGATE_TYPE aggregateType;

    /**
     * aggregate 의 식별자. 여러 종류의 aggregate 키를 한 컬럼에 담으므로 문자열로 둔다.
     */
    @Column(nullable = false, updatable = false)
    private String aggregateId;

    protected PolymorphicOutboxEntity() {
    }

    protected PolymorphicOutboxEntity(EVENT_ID eventId, AGGREGATE_TYPE aggregateType, String aggregateId,
                                      EVENT_TYPE eventType, String payload) {
        super(eventId, eventType, payload);
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
    }
}
