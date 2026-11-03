package com.iturtle.integration.jpa;

import com.iturtle.jpa.auditing.SecurityAuditorAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 서비스와 같은 조건(자동 설정만으로 Auditing 이 켜짐)에서 엔티티 상위 클래스들의 ~At / ~By 를 검증한다.
 */
@SpringBootTest(classes = JpaAuditingIntegrationTest.TestApplication.class)
class JpaAuditingIntegrationTest {

    @Autowired
    private TestOrderRepository orderRepository;

    @Autowired
    private TestCodeRepository codeRepository;

    @Autowired
    private TestLogRepository logRepository;

    @Autowired
    private ApplicationContext context;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        orderRepository.deleteAll();
        codeRepository.deleteAll();
        logRepository.deleteAll();
    }

    @Test
    void securityAuditorAwareRegisteredByAutoConfiguration() {
        assertEquals(1, context.getBeansOfType(SecurityAuditorAware.class).size());
    }

    @Test
    void createdAtOnly() {
        assertNotNull(logRepository.saveAndFlush(new TestLog()).getCreatedAt());
    }

    @Test
    void createdAndUpdatedFilledFromLoginUser() throws Exception {
        login("user-1");
        Long id = orderRepository.saveAndFlush(new TestOrder("first")).getId();
        TestOrder created = orderRepository.findById(id).orElseThrow();

        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        assertEquals("user-1", created.getCreatedBy());
        assertEquals("user-1", created.getUpdatedBy());
        assertFalse(created.isDeleted());

        Thread.sleep(10);
        login("user-2");
        created.rename("second");
        orderRepository.saveAndFlush(created);
        TestOrder updated = orderRepository.findById(id).orElseThrow();

        assertEquals(created.getCreatedAt(), updated.getCreatedAt());
        assertTrue(updated.getUpdatedAt().isAfter(created.getUpdatedAt()));
        assertEquals("user-1", updated.getCreatedBy());   // 생성자는 바뀌지 않는다
        assertEquals("user-2", updated.getUpdatedBy());
    }

    @Test
    void softDeleteFillsDeletedAtAndDeletedBy() {
        login("user-1");
        Long id = orderRepository.saveAndFlush(new TestOrder("order")).getId();

        login("user-3");
        TestOrder order = orderRepository.findById(id).orElseThrow();
        order.delete();
        orderRepository.saveAndFlush(order);
        TestOrder deleted = orderRepository.findById(id).orElseThrow();

        assertTrue(deleted.isDeleted());
        assertNotNull(deleted.getDeletedAt());
        assertEquals("user-3", deleted.getDeletedBy());
        assertEquals("user-3", deleted.getUpdatedBy());
        assertEquals("user-1", deleted.getCreatedBy());

        // 이미 삭제된 엔티티를 다시 삭제해도 처음 삭제 기록을 유지한다
        Instant firstDeletedAt = deleted.getDeletedAt();
        login("user-4");
        deleted.delete();
        deleted.rename("changed");
        orderRepository.saveAndFlush(deleted);
        TestOrder again = orderRepository.findById(id).orElseThrow();
        assertEquals(firstDeletedAt, again.getDeletedAt());
        assertEquals("user-3", again.getDeletedBy());

        again.restore();
        orderRepository.saveAndFlush(again);
        TestOrder restored = orderRepository.findById(id).orElseThrow();
        assertFalse(restored.isDeleted());
        assertNull(restored.getDeletedBy());
    }

    @Test
    void softDeleteWithoutBy() {
        Long id = codeRepository.saveAndFlush(new TestCode()).getId();
        TestCode code = codeRepository.findById(id).orElseThrow();

        code.delete();
        codeRepository.saveAndFlush(code);

        assertTrue(codeRepository.findById(id).orElseThrow().isDeleted());
    }

    @Test
    void byLeftNullWithoutLoginUser() {
        TestOrder withoutLogin = orderRepository.saveAndFlush(new TestOrder("batch"));

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        TestOrder anonymous = orderRepository.saveAndFlush(new TestOrder("anonymous"));

        assertNotNull(withoutLogin.getCreatedAt());
        assertNull(withoutLogin.getCreatedBy());
        assertNull(anonymous.getCreatedBy());
    }

    private void login(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, null, List.of()));
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
