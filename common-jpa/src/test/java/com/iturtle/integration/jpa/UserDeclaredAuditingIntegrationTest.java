package com.iturtle.integration.jpa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 서비스가 &#64;EnableJpaAuditing 과 AuditorAware 를 직접 선언해도 중복 등록 없이 서비스 설정이 사용되는지 검증한다.
 */
@SpringBootTest(classes = UserDeclaredAuditingIntegrationTest.TestApplication.class)
class UserDeclaredAuditingIntegrationTest {

    @Autowired
    private TestOrderRepository repository;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void serviceAuditorAwareUsed() {
        TestOrder saved = repository.saveAndFlush(new TestOrder("batch"));

        assertEquals("system", saved.getCreatedBy());
    }

    @SpringBootApplication
    @EnableJpaAuditing
    static class TestApplication {

        @Bean
        AuditorAware<String> systemAuditorAware() {
            return () -> Optional.of("system");
        }
    }
}
