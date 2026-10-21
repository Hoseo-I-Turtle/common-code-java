package com.iturtle.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

/**
 * 생성 / 수정 시각을 기록한다.
 */
@Getter
@MappedSuperclass
public abstract class TimeEntity extends CreatedAtEntity {

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
