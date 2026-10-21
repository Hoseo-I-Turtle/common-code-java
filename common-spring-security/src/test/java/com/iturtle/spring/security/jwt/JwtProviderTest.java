package com.iturtle.spring.security.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration SKEW = Duration.ofSeconds(30);

    @Test
    void hs256CreateAndParse() throws ParseException {
        JwtProvider provider = provider("access", hs256());

        String token = provider.createToken("user-1", Map.of("roles", List.of("ROLE_USER")));
        JWTClaimsSet claims = provider.parse(token);

        assertEquals("user-1", claims.getSubject());
        assertEquals(List.of("ROLE_USER"), claims.getStringListClaim("roles"));
    }

    @Test
    void expirationFromTokenSettings() {
        JwtProperties.Token settings = hs256();
        settings.setExpiration(Duration.ofDays(14));
        JwtProvider provider = provider("refresh", settings);

        JWTClaimsSet claims = provider.parse(provider.createToken("user-1", Map.of()));

        assertEquals(Duration.ofDays(14),
                Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()));
    }

    @Test
    void otherTokenTypeRejectedEvenWithSameKey() {
        String refreshToken = provider("refresh", hs256()).createToken("user-1", Map.of());

        InvalidJwtException e = assertThrows(InvalidJwtException.class, () -> provider("access", hs256()).parse(refreshToken));

        assertEquals(JwtErrorCode.INVALID_TOKEN, e.getErrorCode());
    }

    @Test
    void claimsCannotOverrideStandardClaims() {
        JwtProvider provider = provider("access", hs256());

        JWTClaimsSet claims = provider.parse(provider.createToken("user-1", Map.of("sub", "admin")));

        assertEquals("user-1", claims.getSubject());
    }

    @Test
    void expiredTokenRejectedAsExpired() {
        String token = provider("access", hs256(), NOW).createToken("user-1", Map.of(), Duration.ofMinutes(5));

        JwtProvider later = provider("access", hs256(), NOW.plus(Duration.ofMinutes(10)));
        InvalidJwtException e = assertThrows(InvalidJwtException.class, () -> later.parse(token));

        assertTrue(e.isExpired());
        assertEquals(JwtErrorCode.EXPIRED_TOKEN, e.getErrorCode());
    }

    @Test
    void tokenWithinClockSkewAccepted() {
        String token = provider("access", hs256(), NOW).createToken("user-1", Map.of(), Duration.ofMinutes(5));

        JwtProvider slightlyLater = provider("access", hs256(), NOW.plus(Duration.ofMinutes(5)).plusSeconds(10));

        assertEquals("user-1", slightlyLater.parse(token).getSubject());
    }

    @Test
    void tamperedOrMalformedTokenRejectedAsInvalid() {
        JwtProvider provider = provider("access", hs256());
        String token = provider.createToken("user-1", Map.of());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertEquals(JwtErrorCode.INVALID_TOKEN, assertThrows(InvalidJwtException.class, () -> provider.parse(tampered)).getErrorCode());
        assertEquals(JwtErrorCode.INVALID_TOKEN, assertThrows(InvalidJwtException.class, () -> provider.parse("abc")).getErrorCode());
    }

    @Test
    void tokenFromOtherSecretRejected() {
        JwtProperties.Token other = hs256();
        other.setSecret("ffffffffffffffffffffffffffffffff");
        String token = provider("access", other).createToken("user-1", Map.of());

        assertThrows(InvalidJwtException.class, () -> provider("access", hs256()).parse(token));
    }

    @Test
    void issuerMismatchRejected() {
        JwtProperties.Token issuerA = hs256();
        issuerA.setIssuer("a");
        JwtProperties.Token issuerB = hs256();
        issuerB.setIssuer("b");
        String token = provider("access", issuerA).createToken("user-1", Map.of());

        assertThrows(InvalidJwtException.class, () -> provider("access", issuerB).parse(token));
    }

    @Test
    void shortSecretFailsAtStartup() {
        JwtProperties.Token settings = hs256();
        settings.setSecret("short");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> provider("access", settings));

        assertTrue(e.getMessage().contains("iturtle.security.jwt.tokens.access.secret"));
    }

    @Test
    void rs256IssuerSignsAndVerifierOnlyVerifies() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        JwtProvider issuer = provider("access", rs256(keyPair, true));
        JwtProvider verifier = provider("access", rs256(keyPair, false));

        String token = issuer.createToken("user-1", Map.of());

        assertEquals("user-1", verifier.parse(token).getSubject());
        assertThrows(IllegalStateException.class, () -> verifier.createToken("user-1", Map.of()));
    }

    @Test
    void hs256TokenRejectedByRs256Provider() throws Exception {
        String hsToken = provider("access", hs256()).createToken("user-1", Map.of());
        JwtProvider rsProvider = provider("access", rs256(rsaKeyPair(), false));

        assertThrows(InvalidJwtException.class, () -> rsProvider.parse(hsToken));
    }

    @Test
    void providersByName() {
        JwtProperties properties = new JwtProperties();
        properties.getTokens().put("access", hs256());
        properties.getTokens().put("refresh", hs256());

        JwtProviders providers = new JwtProviders(properties);

        assertEquals("refresh", providers.get("refresh").getName());
        assertThrows(IllegalArgumentException.class, () -> providers.get("unknown"));
        assertThrows(IllegalStateException.class, () -> new JwtProviders(new JwtProperties()));
    }

    private JwtProvider provider(String name, JwtProperties.Token settings) {
        return new JwtProvider(name, settings, SKEW, Clock.systemUTC());
    }

    private JwtProvider provider(String name, JwtProperties.Token settings, Instant now) {
        return new JwtProvider(name, settings, SKEW, Clock.fixed(now, ZoneOffset.UTC));
    }

    private JwtProperties.Token hs256() {
        JwtProperties.Token settings = new JwtProperties.Token();
        settings.setSecret(SECRET);
        return settings;
    }

    private JwtProperties.Token rs256(KeyPair keyPair, boolean withPrivateKey) {
        JwtProperties.Token settings = new JwtProperties.Token();
        settings.setAlgorithm(JwtAlgorithm.RS256);
        settings.setPublicKey(pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        if (withPrivateKey) {
            settings.setPrivateKey(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        }
        return settings;
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }
}
