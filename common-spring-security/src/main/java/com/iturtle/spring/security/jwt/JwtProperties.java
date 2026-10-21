package com.iturtle.spring.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 토큰 종류(access, refresh 등)마다 서명 방식 / 키 / 유효 기간을 따로 설정한다. 이름은 자유롭게 정할 수 있다.
 * <pre>
 * iturtle:
 *   security:
 *     jwt:
 *       enabled: true
 *       tokens:
 *         access:
 *           algorithm: RS256                    # HS256(기본) | RS256
 *           public-key: ${JWT_ACCESS_PUBLIC_KEY}   # PEM (-----BEGIN PUBLIC KEY-----)
 *           private-key: ${JWT_ACCESS_PRIVATE_KEY} # PEM PKCS#8. 발급하는 서비스만 설정
 *           issuer: iturtle-auth
 *           expiration: 30m
 *         refresh:                              # 인증 서비스만 설정
 *           secret: ${JWT_REFRESH_SECRET}       # HS256. 32바이트 이상
 *           expiration: 14d
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "iturtle.security.jwt")
public class JwtProperties {

    /** true 일 때만 JwtProviders 를 등록한다 */
    private boolean enabled = false;

    /** 토큰 이름별 설정 */
    private Map<String, Token> tokens = new LinkedHashMap<>();

    /** 검증 시 허용하는 서버 간 시간 오차 (모든 토큰 공통) */
    private Duration clockSkew = Duration.ofSeconds(30);

    @Getter
    @Setter
    public static class Token {

        private JwtAlgorithm algorithm = JwtAlgorithm.HS256;

        /** HS256 서명 secret (UTF-8 기준 32바이트 이상) */
        private String secret;

        /** RS256 검증용 public key (PEM) */
        private String publicKey;

        /** RS256 발급용 private key (PEM, PKCS#8). 없으면 검증만 가능하다 */
        private String privateKey;

        /** 발급 시 iss 에 넣고, 검증 시 일치하는지 확인한다. 비우면 확인하지 않는다 */
        private String issuer;

        /** createToken(subject, claims) 로 발급하는 토큰의 유효 기간 */
        private Duration expiration = Duration.ofMinutes(30);
    }
}
