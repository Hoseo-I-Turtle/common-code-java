package com.iturtle.spring.security.jwt;

/**
 * JWT 서명 방식.
 * <ul>
 *     <li>HS256: 공유 secret 하나로 발급 / 검증. secret 을 가진 모든 서비스가 토큰을 발급할 수 있다</li>
 *     <li>RS256: private key 로 발급, public key 로 검증. 검증 서비스는 public key 만 가지므로 발급할 수 없다</li>
 * </ul>
 */
public enum JwtAlgorithm {
    HS256,
    RS256
}
