package com.iturtle.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 해시와 서명.
 * <ul>
 *   <li>토큰 원문을 저장하지 않기 위한 단방향 해시: SHA-256 hex(64자). 비밀번호처럼 짧고 추측 가능한 값이 아니라
 *       충분히 긴 무작위 토큰(JWT, 인증번호 토큰 등)용이다. 비밀번호에는 쓰지 않는다.</li>
 *   <li>파일처럼 큰 내용의 SHA-256: 스트림을 끝까지 읽으며 계산한다 (무결성 확인용).</li>
 *   <li>공유 비밀로 만든 서명(HMAC-SHA256): 웹훅처럼 보낸 쪽이 같은 비밀을 아는지 확인할 때 쓴다.</li>
 * </ul>
 * 비교 메서드(matches*, constantTimeEquals)는 모두 비교 시간이 값에 따라 달라지지 않는다.
 * <pre>
 * // GitHub 웹훅: X-Hub-Signature-256 = "sha256=" + hex
 * boolean valid = HashUtils.matchesHmacSha256Hex(secret, rawBody, signatureHeader.substring("sha256=".length()));
 * </pre>
 */
public final class HashUtils {

    private static final String ALGORITHM = "SHA-256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();
    private static final int BUFFER_SIZE = 8192;

    private HashUtils() {}

    public static String sha256Hex(String token) {
        return HEX.formatHex(digest().digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * token 의 해시가 tokenHash 와 같은지 확인한다. 비교 시간이 값에 따라 달라지지 않는다.
     */
    public static boolean matchesSha256Hex(String token, String tokenHash) {
        if (token == null || tokenHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256Hex(token).getBytes(StandardCharsets.UTF_8),
                tokenHash.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 스트림을 끝까지 읽어 SHA-256 hex(소문자 64자)를 만든다. 스트림은 호출한 쪽이 닫는다.
     *
     * @throws UncheckedIOException 읽기에 실패하면
     */
    public static String sha256Hex(InputStream content) {
        MessageDigest digest = digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int read;
            while ((read = content.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("SHA-256 계산 중 읽기에 실패했다", e);
        }
        return HEX.formatHex(digest.digest());
    }

    /**
     * message 를 secret 으로 서명한 HMAC-SHA256 hex(소문자 64자).
     *
     * @throws IllegalArgumentException secret 이 비어 있으면
     */
    public static String hmacSha256Hex(byte[] secret, byte[] message) {
        return HEX.formatHex(hmac(secret).doFinal(message));
    }

    /**
     * 문자열 비밀 · 본문은 UTF-8 바이트로 서명한다.
     */
    public static String hmacSha256Hex(String secret, String message) {
        return hmacSha256Hex(secret.getBytes(StandardCharsets.UTF_8), message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * message 의 HMAC-SHA256 이 expectedHex 와 같은지 확인한다. 비교 시간이 값에 따라 달라지지 않는다.
     * hex 는 대소문자를 가리지 않는다. 값 중 하나라도 null 이거나, secret 이 비었거나, expectedHex 가 hex 가 아니면 false.
     * <p>
     * 서명은 받은 본문 바이트 그대로 계산한다. JSON 을 파싱했다가 다시 직렬화한 값으로 계산하면 맞지 않는다.
     */
    public static boolean matchesHmacSha256Hex(byte[] secret, byte[] message, String expectedHex) {
        if (secret == null || secret.length == 0 || message == null || expectedHex == null) {
            return false;
        }
        byte[] expected;
        try {
            expected = HEX.parseHex(expectedHex.toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(hmac(secret).doFinal(message), expected);
    }

    /**
     * 두 문자열이 같은지 확인한다. 비교 시간이 내용에 따라 달라지지 않는다 (길이가 다르면 바로 false).
     * 비밀값 · 서명 · 토큰을 비교할 때 {@code equals} 대신 쓴다. null 은 어느 쪽이든 false.
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " 알고리즘을 사용할 수 없다", e);
        }
    }

    private static Mac hmac(byte[] secret) {
        if (secret.length == 0) {
            throw new IllegalArgumentException("HMAC 비밀은 비어 있을 수 없다");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HMAC_ALGORITHM + " 알고리즘을 사용할 수 없다", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("HMAC 비밀을 쓸 수 없다", e);
        }
    }
}
