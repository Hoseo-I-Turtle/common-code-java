package com.iturtle.spring.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * 토큰 한 종류(access, refresh 등)의 발급 / 검증. JwtProviders 에서 이름으로 꺼내 사용한다.
 * <pre>
 * String token = jwtProviders.get("access").createToken(userId, Map.of("roles", List.of("ROLE_USER")));
 * JWTClaimsSet claims = jwtProviders.get("access").parse(token);   // 실패하면 InvalidJwtException
 * </pre>
 * 헤더 typ 에 토큰 이름("access+jwt")을 넣고 검증 시 확인하므로, 같은 키를 쓰더라도 refresh 토큰을 access 토큰으로 쓸 수 없다.
 * RS256 에서 private-key 가 없으면 검증만 가능하고, 발급하면 IllegalStateException 이 발생한다.
 * 설정이 잘못되었으면(secret 길이 부족, PEM 형식 오류 등) 애플리케이션 시작 시 실패한다.
 */
public class JwtProvider {

    private static final int MIN_SECRET_BYTES = 32;

    private final String name;
    private final JwtProperties.Token settings;
    private final Duration clockSkew;
    private final Clock clock;
    private final JOSEObjectType type;
    private final JWSAlgorithm algorithm;
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    public JwtProvider(String name, JwtProperties.Token settings, Duration clockSkew, Clock clock) {
        this.name = name;
        this.settings = settings;
        this.clockSkew = clockSkew;
        this.clock = clock;
        this.type = new JOSEObjectType(name + "+jwt");
        try {
            if (settings.getAlgorithm() == JwtAlgorithm.RS256) {
                this.algorithm = JWSAlgorithm.RS256;
                this.verifier = new RSASSAVerifier(publicKey(require(settings.getPublicKey(), "public-key")));
                this.signer = settings.getPrivateKey() != null && !settings.getPrivateKey().isBlank()
                        ? new RSASSASigner(privateKey(settings.getPrivateKey()))
                        : null;
            } else {
                byte[] secret = require(settings.getSecret(), "secret").getBytes(StandardCharsets.UTF_8);
                if (secret.length < MIN_SECRET_BYTES) {
                    throw new IllegalArgumentException(
                            property("secret") + " 은 " + MIN_SECRET_BYTES + "바이트 이상이어야 한다 (현재 " + secret.length + ")");
                }
                this.algorithm = JWSAlgorithm.HS256;
                this.signer = new MACSigner(secret);
                this.verifier = new MACVerifier(secret);
            }
        } catch (JOSEException | GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(property("") + " 키 설정이 올바르지 않다: " + e.getMessage(), e);
        }
    }

    public String getName() {
        return name;
    }

    /**
     * 설정된 expiration 동안 유효한 토큰을 발급한다.
     */
    public String createToken(String subject, Map<String, ?> claims) {
        return createToken(subject, claims, settings.getExpiration());
    }

    public String createToken(String subject, Map<String, ?> claims, Duration expiration) {
        if (signer == null) {
            throw new IllegalStateException(property("private-key") + " 가 없어 " + name + " 토큰을 발급할 수 없다 (검증 전용)");
        }
        Instant now = clock.instant();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        // 표준 클레임(sub, exp 등)을 claims 로 덮어쓰지 못하도록 나중에 넣는다
        claims.forEach(builder::claim);
        builder.subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(expiration)));
        if (settings.getIssuer() != null) {
            builder.issuer(settings.getIssuer());
        }

        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(type).build(), builder.build());
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException(name + " 토큰 서명에 실패했다", e);
        }
        return jwt.serialize();
    }

    /**
     * 토큰 종류(typ), 서명, 만료, issuer 를 검증하고 클레임을 반환한다. 실패하면 InvalidJwtException.
     */
    public JWTClaimsSet parse(String token) {
        SignedJWT jwt;
        JWTClaimsSet claims;
        try {
            jwt = SignedJWT.parse(token);
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw invalid("형식이 올바르지 않은 토큰", e);
        }

        if (!type.equals(jwt.getHeader().getType())) {
            throw invalid("토큰 종류 불일치 (기대 " + type + ", 실제 " + jwt.getHeader().getType() + ")", null);
        }
        // 헤더의 alg 를 믿고 검증 방식을 바꾸면 alg 바꿔치기 공격에 노출되므로 설정된 방식만 허용한다
        if (!algorithm.equals(jwt.getHeader().getAlgorithm())) {
            throw invalid("허용하지 않는 서명 방식 " + jwt.getHeader().getAlgorithm(), null);
        }
        try {
            if (!jwt.verify(verifier)) {
                throw invalid("서명 불일치", null);
            }
        } catch (JOSEException e) {
            throw invalid("서명 검증 실패", e);
        }

        verifyClaims(claims);
        return claims;
    }

    private void verifyClaims(JWTClaimsSet claims) {
        if (claims.getSubject() == null || claims.getExpirationTime() == null) {
            throw invalid("sub / exp 클레임 누락", null);
        }
        Instant now = clock.instant();
        if (now.minus(clockSkew).isAfter(claims.getExpirationTime().toInstant())) {
            throw new InvalidJwtException(JwtErrorCode.EXPIRED_TOKEN,
                    "만료된 " + name + " 토큰 (exp " + claims.getExpirationTime().toInstant() + ")");
        }
        if (claims.getNotBeforeTime() != null && now.plus(clockSkew).isBefore(claims.getNotBeforeTime().toInstant())) {
            throw invalid("아직 유효하지 않은 토큰 (nbf)", null);
        }
        if (settings.getIssuer() != null && !settings.getIssuer().equals(claims.getIssuer())) {
            throw invalid("issuer 불일치 " + claims.getIssuer(), null);
        }
    }

    private InvalidJwtException invalid(String message, Throwable cause) {
        return new InvalidJwtException(JwtErrorCode.INVALID_TOKEN, name + " " + message, cause);
    }

    private String require(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property(key) + " 가 필요하다");
        }
        return value;
    }

    private String property(String key) {
        return "iturtle.security.jwt.tokens." + name + (key.isEmpty() ? "" : "." + key);
    }

    private static RSAPublicKey publicKey(String pem) throws GeneralSecurityException {
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pemBody(pem)));
    }

    private static RSAPrivateKey privateKey(String pem) throws GeneralSecurityException {
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pemBody(pem)));
    }

    /**
     * PEM 의 BEGIN / END 줄과 공백을 제거하고 Base64 로 디코딩한다.
     */
    private static byte[] pemBody(String pem) {
        String body = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
