package com.iturtle.jpa.entity;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;

/**
 * 생성 / 수정 시각과 생성자 / 수정자에 더해 삭제 시각 / 삭제자를 기록한다 (soft delete). 사용법은 SoftDeletable 참고.
 * <p>
 * delete() 는 삭제 시각만 기록하고, 삭제자는 저장 직전에 SoftDeleteAuditingListener 가 AuditorAware 로 채운다
 * (createdBy / updatedBy 와 같은 사용자).
 */
@Getter
@MappedSuperclass
@EntityListeners(SoftDeleteAuditingListener.class)
public abstract class SoftDeleteAuditEntity extends AuditEntity implements SoftDeletable {

    private Instant deletedAt;

    private String deletedBy;

    @Override
    public void delete() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }

    @Override
    public void restore() {
        deletedAt = null;
        deletedBy = null;
    }

    /**
     * SoftDeleteAuditingListener 가 호출한다.
     */
    void markDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }
}
