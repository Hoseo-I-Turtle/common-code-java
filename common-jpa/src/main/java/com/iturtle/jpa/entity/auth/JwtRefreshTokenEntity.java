package com.iturtle.jpa.entity.auth;

import com.iturtle.core.util.TokenHashUtils;
import com.iturtle.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 발급한 JWT refresh 토큰. 재발급할 때 기존 토큰을 폐기하고 새 토큰을 저장하는 로테이션 방식으로 쓴다.
 * <p>
 * 토큰 원문은 저장하지 않고 SHA-256 해시만 담는다 (생성자가 원문을 받아 해시한다). 조회는 jti 로 한다.
 * 이미 폐기된 토큰이 다시 들어오면 탈취로 보고 같은 familyId 의 토큰을 모두 폐기한다.
 * <ul>
 *     <li>SUBJECT_ID: subject / revokedBy 의 타입. 회원 PK 타입(Long, UUID 등)에 맞추면 그 타입의 컬럼이 된다</li>
 *     <li>REVOKE_REASON: 폐기 사유. enum 이나 String 이며 문자열로 저장한다 (enum 은 이름)</li>
 * </ul>
 * jti / familyId / previousJti 는 JwtProvider 가 발급하는 jti 와 같은 UUID 다. familyId 는 IdGenerateUtils.getUuidV7() 로 만든다.
 * <pre>
 * public enum RevokeReason { ROTATED, LOGOUT, REUSE_DETECTED }
 *
 * &#64;Entity
 * &#64;Table(name = "refresh_tokens")
 * public class RefreshToken extends JwtRefreshTokenEntity&lt;Long, RevokeReason&gt; {
 *     &#64;Id &#64;GeneratedValue(strategy = GenerationType.IDENTITY)
 *     private Long id;
 * }
 * </pre>
 * 회원별 / familyId 별 조회 인덱스는 자식 엔티티의 &#64;Table 에 선언한다.
 */
@Getter
@MappedSuperclass
public abstract class JwtRefreshTokenEntity<SUBJECT_ID, REVOKE_REASON> extends CreatedAtEntity {

    /**
     * 토큰의 sub (회원 식별자).
     */
    @Column(nullable = false, updatable = false)
    private SUBJECT_ID subject;

    /**
     * 로그인 한 번으로 시작된 로테이션 체인의 식별자. 재사용이 탐지되면 이 단위로 폐기한다.
     */
    @Column(nullable = false, updatable = false)
    private UUID familyId;

    /**
     * 토큰의 jti. 토큰 원문 대신 이 값으로 조회한다.
     */
    @Column(nullable = false, updatable = false, unique = true)
    private UUID jti;

    /**
     * 이 토큰을 발급하며 폐기한 직전 토큰의 jti. 로그인으로 처음 발급된 토큰은 null 이다.
     */
    @Column(updatable = false)
    private UUID previousJti;

    /**
     * 토큰 원문의 SHA-256 hex. 서명 키가 유출되어 위조된 토큰이라도 이 값과 맞지 않으면 거부한다.
     */
    @Column(nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    /**
     * 로테이션 또는 로그아웃으로 폐기된 시각. 살아 있는 토큰은 null 이다.
     */
    private Instant revokedAt;

    /**
     * 폐기를 유발한 회원. 시스템이 폐기했으면 null 이다.
     */
    private SUBJECT_ID revokedBy;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    private REVOKE_REASON revokedReason;

    protected JwtRefreshTokenEntity() {
    }

    protected JwtRefreshTokenEntity(SUBJECT_ID subject, UUID familyId, UUID jti, UUID previousJti, String rawToken,
                                    Instant expiresAt) {
        this.subject = subject;
        this.familyId = familyId;
        this.jti = jti;
        this.previousJti = previousJti;
        this.tokenHash = TokenHashUtils.sha256Hex(rawToken);
        this.expiresAt = expiresAt;
    }

    /**
     * 폐기한다. 이미 폐기되었으면 처음 기록을 유지한다.
     */
    public void revoke(SUBJECT_ID revokedBy, REVOKE_REASON revokedReason) {
        if (revokedAt == null) {
            this.revokedAt = Instant.now();
            this.revokedBy = revokedBy;
            this.revokedReason = revokedReason;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return !expiresAt.isAfter(Instant.now());
    }

    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }

    /**
     * rawToken 이 저장한 토큰과 같은지 해시로 확인한다.
     */
    public boolean matches(String rawToken) {
        return TokenHashUtils.matches(rawToken, tokenHash);
    }
}
