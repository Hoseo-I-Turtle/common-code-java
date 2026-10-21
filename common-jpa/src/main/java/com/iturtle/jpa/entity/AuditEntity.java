package com.iturtle.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * 생성 / 수정 시각에 더해 생성자 / 수정자를 기록한다.
 * <p>
 * 값은 AuditorAware&lt;String&gt; 빈이 정한다. Spring Security 가 있으면 자동 설정이 로그인 사용자의
 * Authentication.getName()(JWT 를 쓰면 sub)을 넣는 구현을 등록한다. 로그인하지 않은 요청이나 배치처럼 사용자가 없으면 null 이다.
 * 다른 값(예: 사번)을 넣으려면 AuditorAware&lt;String&gt; 빈을 직접 등록한다.
 */
@Getter
@MappedSuperclass
public abstract class AuditEntity extends TimeEntity {

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}
