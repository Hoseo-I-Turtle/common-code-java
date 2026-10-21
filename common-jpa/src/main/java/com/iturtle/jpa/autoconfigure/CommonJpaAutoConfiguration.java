package com.iturtle.jpa.autoconfigure;

import com.iturtle.jpa.auditing.SecurityAuditorAware;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing(CreatedAtEntity 계열 상위 클래스의 ~At / ~By)을 켠다. iturtle.jpa.auditing.enabled=false 로 끌 수 있다.
 * <ul>
 *     <li>서비스가 이미 &#64;EnableJpaAuditing 을 선언했으면 다시 켜지 않는다</li>
 *     <li>Spring Security 가 있고 AuditorAware 빈이 없으면 SecurityAuditorAware 를 등록한다</li>
 * </ul>
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ConditionalOnClass(AuditingEntityListener.class)
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnProperty(prefix = "iturtle.jpa.auditing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CommonJpaAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(name = "jpaAuditingHandler")
    @EnableJpaAuditing
    static class AuditingConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    static class SecurityAuditorConfiguration {

        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        AuditorAware<String> securityAuditorAware() {
            return new SecurityAuditorAware();
        }
    }
}
