package com.iturtle.spring.web.logging;

/**
 * 접근 로그 한 줄에 필요한 값. MVC / WebFlux 필터가 각자의 요청 객체에서 꺼내 만든다.
 *
 * @param body          캐시된 요청 본문 (없으면 빈 배열)
 * @param contentLength 요청의 Content-Length (모르면 -1). body 보다 크면 잘린 것으로 표시한다
 */
public record AccessLogEntry(
        String method,
        String path,
        String query,
        int status,
        long durationNanos,
        String clientIp,
        String contentType,
        String characterEncoding,
        byte[] body,
        long contentLength
) {
}
