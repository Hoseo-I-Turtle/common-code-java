package com.iturtle.core.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 토큰 원문을 저장하지 않기 위한 단방향 해시. 길이가 고정된 SHA-256 hex(64자)를 쓴다.
 * <p>
 * 비밀번호처럼 짧고 추측 가능한 값이 아니라 충분히 긴 무작위 토큰(JWT, 인증번호 토큰 등)용이다. 비밀번호에는 쓰지 않는다.
 */
public final class HashUtils {

    private static final String ALGORITHM = "SHA-256";
    private static final HexFormat HEX = HexFormat.of();

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

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " 알고리즘을 사용할 수 없다", e);
        }
    }
}
