package com.iturtle.jpa.entity;

import jakarta.persistence.PreUpdate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.AuditorAware;

/**
 * 삭제된(deletedAt 이 있는) SoftDeleteAuditEntity 를 저장하기 직전에 deletedBy 를 AuditorAware 로 채운다.
 * <p>
 * Spring Boot 는 JPA 엔티티 리스너를 Spring 빈으로 만들어 의존성을 주입한다. AuditorAware 가 없거나 사용자가 없으면 비워 둔다.
 */
public class SoftDeleteAuditingListener {

    private ObjectProvider<AuditorAware<?>> auditorAware;

    @Autowired
    public void setAuditorAware(ObjectProvider<AuditorAware<?>> auditorAware) {
        this.auditorAware = auditorAware;
    }

    @PreUpdate
    public void fillDeletedBy(SoftDeleteAuditEntity entity) {
        if (entity.getDeletedAt() == null || entity.getDeletedBy() != null || auditorAware == null) {
            return;
        }
        AuditorAware<?> auditor = auditorAware.getIfAvailable();
        if (auditor != null) {
            auditor.getCurrentAuditor().map(String::valueOf).ifPresent(entity::markDeletedBy);
        }
    }
}
