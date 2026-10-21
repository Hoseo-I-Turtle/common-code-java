package com.iturtle.spring.security.jwt;

import com.nimbusds.jwt.JWTClaimsSet;

import java.time.Clock;
import java.time.Duration;

/**
 * 토큰 무효화(로그아웃, 탈취 대응) 저장소. 서비스가 구현한다 (여러 인스턴스가 공유해야 하므로 보통 Redis).
 * <ul>
 *     <li>토큰 하나: jti(JwtProvider 가 발급 시 넣는다)를 키로 저장한다</li>
 *     <li>사용자의 모든 토큰(모든 기기 로그아웃, 비밀번호 변경): sub 별 기준 시각을 저장하고 iat 가 그 이전이면 무효로 본다</li>
 * </ul>
 * 만료된 토큰은 검증에서 이미 거부되므로, 항목은 토큰 만료 시각까지만 보관하면 된다 (remainingLifetime 을 TTL 로 사용).
 * <pre>
 * // 로그아웃: JwtAuthenticationFilter 가 details 에 넣은 클레임으로 무효화
 * tokenBlacklist.revoke((JWTClaimsSet) authentication.getDetails());
 * </pre>
 */
public interface TokenBlacklist {

    void revoke(JWTClaimsSet claims);

    boolean isRevoked(JWTClaimsSet claims);

    /**
     * 토큰이 만료될 때까지 남은 시간. 이미 만료되었으면 0.
     */
    static Duration remainingLifetime(JWTClaimsSet claims) {
        return remainingLifetime(claims, Clock.systemUTC());
    }

    static Duration remainingLifetime(JWTClaimsSet claims, Clock clock) {
        if (claims.getExpirationTime() == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(clock.instant(), claims.getExpirationTime().toInstant());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
