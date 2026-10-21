package com.iturtle.spring.security.webmvc;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Authorization: Bearer 헤더에서 토큰을 꺼낸다. JWT 필터들이 같은 규칙으로 읽도록 공유한다.
 */
final class BearerTokens {

    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokens() {}

    /**
     * 헤더가 없거나 Bearer 가 아니거나 토큰이 비어 있으면 null.
     */
    static String resolve(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
