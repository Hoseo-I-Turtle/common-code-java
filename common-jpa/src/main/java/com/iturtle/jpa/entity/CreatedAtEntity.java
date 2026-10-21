package com.iturtle.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 생성 시각만 기록한다. 수정되지 않는 기록성 데이터(로그, 이력)에 사용한다.
 * <pre>
 * CreatedAtEntity                 createdAt
 *  └ TimeEntity                   + updatedAt
 *     ├ SoftDeleteTimeEntity      + deletedAt
 *     └ AuditEntity               + createdBy, updatedBy
 *        └ SoftDeleteAuditEntity  + deletedAt, deletedBy
 * </pre>
 * 시각은 서버 시간대와 무관한 Instant(UTC)로 저장한다. 값은 JPA Auditing 이 채우므로 직접 설정하지 않는다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class CreatedAtEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
