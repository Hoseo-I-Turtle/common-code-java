package com.iturtle.spring.web.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

/**
 * 외부 호출을 com.iturtle.client 로거로 한 줄 기록한다 (ECS 필드).
 * <pre>
 * http.request.method         요청 메서드
 * url.full                    요청 URL (쿼리스트링 제외)
 * http.response.status_code   응답 상태 (연결 실패면 없음)
 * event.duration              처리 시간 (나노초)
 * error.type                  연결 실패 / 타임아웃 시 예외 클래스
 * </pre>
 * 4xx / 5xx 응답과 연결 실패는 warn, 그 외에는 info 로 남긴다.
 */
@Slf4j(topic = "com.iturtle.client")
public class ClientLogInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String method = request.getMethod().name();
        String url = withoutQuery(request.getURI());
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            long duration = System.nanoTime() - start;
            int status = response.getStatusCode().value();
            (response.getStatusCode().isError() ? log.atWarn() : log.atInfo())
                    .addKeyValue("http.request.method", method)
                    .addKeyValue("url.full", url)
                    .addKeyValue("http.response.status_code", status)
                    .addKeyValue("event.duration", duration)
                    .log("{} {} {} {}ms", method, url, status, duration / 1_000_000);
            return response;
        } catch (IOException e) {
            long duration = System.nanoTime() - start;
            log.atWarn()
                    .addKeyValue("http.request.method", method)
                    .addKeyValue("url.full", url)
                    .addKeyValue("event.duration", duration)
                    .addKeyValue("error.type", e.getClass().getName())
                    .log("{} {} failed {}ms: {}", method, url, duration / 1_000_000, e.getMessage());
            throw e;
        }
    }

    /**
     * 쿼리스트링에 토큰 / 키가 들어가는 경우가 많아 남기지 않는다.
     */
    private String withoutQuery(URI uri) {
        String value = uri.toString();
        int index = value.indexOf('?');
        return index < 0 ? value : value.substring(0, index);
    }
}
