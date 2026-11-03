package com.iturtle.integration.jpa;

import com.iturtle.core.util.IdGenerateUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * refresh 토큰 상위 클래스(JwtRefreshTokenEntity)의 매핑, 해시 저장, 폐기를 검증한다.
 */
@SpringBootTest(classes = JwtRefreshTokenEntityIntegrationTest.TestApplication.class)
class JwtRefreshTokenEntityIntegrationTest {

    @Autowired
    private TestRefreshTokenRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void storedWithHashAndNativeTypes() {
        UUID jti = IdGenerateUtils.getUuidV7();
        repository.saveAndFlush(token(jti, "raw-token", Duration.ofDays(14)));

        TestRefreshToken found = repository.findByJti(jti).orElseThrow();
        assertEquals(15L, found.getSubject());
        assertNotNull(found.getCreatedAt());
        assertNotEquals("raw-token", found.getTokenHash());
        assertTrue(found.matches("raw-token"));
        assertFalse(found.matches("other-token"));
        assertTrue(found.isUsable());

        // SUBJECT_ID 가 Long 이면 bigint, jti 는 uuid 컬럼이 된다
        assertEquals("BIGINT", columnType("SUBJECT"));
        assertEquals("BIGINT", columnType("REVOKED_BY"));
        assertEquals("UUID", columnType("JTI"));
        assertEquals("CHARACTER VARYING", columnType("REVOKED_REASON"));
    }

    @Test
    void revokeKeepsFirstRecord() {
        UUID jti = IdGenerateUtils.getUuidV7();
        repository.saveAndFlush(token(jti, "raw-token", Duration.ofDays(14)));

        TestRefreshToken token = repository.findByJti(jti).orElseThrow();
        token.revoke(15L, TestRevokeReason.ROTATED);
        repository.saveAndFlush(token);
        TestRefreshToken revoked = repository.findByJti(jti).orElseThrow();
        assertTrue(revoked.isRevoked());
        assertFalse(revoked.isUsable());
        assertEquals(TestRevokeReason.ROTATED, revoked.getRevokedReason());
        assertEquals("ROTATED", jdbcTemplate.queryForObject(
                "select revoked_reason from test_refresh_token", String.class));

        // 이미 폐기된 토큰을 다시 폐기해도 처음 기록을 유지한다
        Instant firstRevokedAt = revoked.getRevokedAt();
        revoked.revoke(99L, TestRevokeReason.LOGOUT);
        repository.saveAndFlush(revoked);
        TestRefreshToken again = repository.findByJti(jti).orElseThrow();
        assertEquals(firstRevokedAt, again.getRevokedAt());
        assertEquals(15L, again.getRevokedBy());
        assertEquals(TestRevokeReason.ROTATED, again.getRevokedReason());
    }

    @Test
    void expiredNotUsable() {
        TestRefreshToken token = repository.saveAndFlush(token(IdGenerateUtils.getUuidV7(), "raw", Duration.ofSeconds(-1)));

        assertTrue(token.isExpired());
        assertFalse(token.isUsable());
    }

    @Test
    void jtiUnique() {
        UUID jti = IdGenerateUtils.getUuidV7();
        repository.saveAndFlush(token(jti, "a", Duration.ofDays(1)));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(token(jti, "b", Duration.ofDays(1))));
    }

    private TestRefreshToken token(UUID jti, String rawToken, Duration expiresIn) {
        return new TestRefreshToken(15L, IdGenerateUtils.getUuidV7(), jti, null, rawToken, Instant.now().plus(expiresIn));
    }

    private String columnType(String column) {
        return jdbcTemplate.queryForObject("select data_type from information_schema.columns "
                + "where table_name = 'TEST_REFRESH_TOKEN' and column_name = ?", String.class, column);
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
