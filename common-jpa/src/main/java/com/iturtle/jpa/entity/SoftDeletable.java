package com.iturtle.jpa.entity;

import java.time.Instant;

/**
 * 행을 지우지 않고 삭제 시각만 기록하는(soft delete) 엔티티.
 * <p>
 * 삭제는 repository.delete() 가 아니라 엔티티의 delete() 로 한다. 삭제된 행을 조회에서 빼는 것은 서비스가 정한다
 * (Hibernate 제약상 엔티티 클래스에 직접 붙여야 한다).
 * <pre>
 * &#64;Entity
 * &#64;SQLRestriction("deleted_at is null")
 * public class Order extends SoftDeleteAuditEntity { ... }
 * </pre>
 */
public interface SoftDeletable {

    Instant getDeletedAt();

    /**
     * 삭제 시각을 기록한다. 이미 삭제되었으면 처음 삭제 시각을 유지한다.
     */
    void delete();

    /**
     * 삭제 기록을 지운다.
     */
    void restore();

    default boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
