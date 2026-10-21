package com.iturtle.jpa.entity;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;

/**
 * 생성 / 수정 시각에 더해 삭제 시각을 기록한다 (soft delete). 사용법은 SoftDeletable 참고.
 */
@Getter
@MappedSuperclass
public abstract class SoftDeleteTimeEntity extends TimeEntity implements SoftDeletable {

    private Instant deletedAt;

    @Override
    public void delete() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }

    @Override
    public void restore() {
        deletedAt = null;
    }
}
