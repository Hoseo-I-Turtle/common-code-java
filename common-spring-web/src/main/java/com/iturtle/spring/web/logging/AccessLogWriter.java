package com.iturtle.spring.web.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.AntPathMatcher;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 접근 로그를 com.iturtle.access 로거로 한 줄 기록한다 (ECS 필드). 4xx / 5xx 응답이면 마스킹된 요청 본문을 함께 남긴다.
 * 요청 객체에 의존하지 않으므로 MVC / WebFlux 필터가 공통으로 사용한다.
 */
@Slf4j(topic = "com.iturtle.access")
public class AccessLogWriter {

    private static final String MASK = "****";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> excludePaths;
    private final Pattern jsonMaskPattern;
    private final Pattern formMaskPattern;

    public AccessLogWriter(AccessLogProperties properties) {
        this.excludePaths = Stream.concat(
                properties.getExcludePaths().stream(),
                properties.getAdditionalExcludePaths().stream()
        ).toList();

        String fields = Stream.concat(
                        properties.getMaskedFields().stream(),
                        properties.getAdditionalMaskedFields().stream())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        if (fields.isEmpty()) {
            this.jsonMaskPattern = null;
            this.formMaskPattern = null;
        } else {
            // "field": "value" 또는 "field": 123
            this.jsonMaskPattern = Pattern.compile(
                    "(\"(?:" + fields + ")\"\\s*:\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}\\]\\s]+)",
                    Pattern.CASE_INSENSITIVE);
            // field=value (쿼리스트링, form)
            this.formMaskPattern = Pattern.compile(
                    "((?:^|&)(?:" + fields + ")=)[^&]*",
                    Pattern.CASE_INSENSITIVE);
        }
    }

    public boolean isExcluded(String path) {
        return excludePaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    public void write(AccessLogEntry entry) {
        LoggingEventBuilder event = log.atInfo()
                .addKeyValue("http.response.status_code", entry.status())
                .addKeyValue("event.duration", entry.durationNanos())
                .addKeyValue("client.ip", entry.clientIp());

        // MDC 필터가 넣었으면 그대로 출력되므로 중복으로 넣지 않는다
        if (MDC.get(WebMdcKeys.HTTP_REQUEST_METHOD) == null) {
            event = event.addKeyValue(WebMdcKeys.HTTP_REQUEST_METHOD, entry.method());
        }
        if (MDC.get(WebMdcKeys.URL_PATH) == null) {
            event = event.addKeyValue(WebMdcKeys.URL_PATH, entry.path());
        }

        if (entry.query() != null) {
            event = event.addKeyValue("url.query", mask(formMaskPattern, entry.query()));
        }

        if (HttpStatusCode.valueOf(entry.status()).isError()) {
            String body = readBody(entry);
            if (body != null) {
                event = event.addKeyValue("http.request.body.content", body);
            }
        }

        event.log("{} {} {} {}ms", entry.method(), entry.path(), entry.status(), entry.durationNanos() / 1_000_000);
    }

    /**
     * JSON / form / text 본문만 남긴다.
     */
    private String readBody(AccessLogEntry entry) {
        String contentType = entry.contentType();
        byte[] content = entry.body();
        if (contentType == null || content == null || content.length == 0) {
            return null;
        }

        String body = new String(content, charset(entry.characterEncoding()));
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.contains("json")) {
            body = mask(jsonMaskPattern, body);
        } else if (type.contains("x-www-form-urlencoded")) {
            body = mask(formMaskPattern, body);
        } else if (!type.startsWith("text/")) {
            return null;
        }

        if (entry.contentLength() > content.length) {
            body += "...(truncated)";
        }
        return body;
    }

    private String mask(Pattern pattern, String value) {
        if (pattern == null) {
            return value;
        }
        return pattern.matcher(value).replaceAll(match -> {
            String prefix = match.group(1);
            boolean quoted = match.groupCount() > 1 && match.group(2).startsWith("\"");
            return Matcher.quoteReplacement(prefix + (quoted ? "\"" + MASK + "\"" : MASK));
        });
    }

    private Charset charset(String encoding) {
        try {
            return encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        } catch (IllegalArgumentException e) {
            return StandardCharsets.UTF_8;
        }
    }
}
