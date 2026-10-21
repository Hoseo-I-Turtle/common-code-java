package com.iturtle.spring.webmvc.logging;

import com.iturtle.spring.core.mdc.MdcContext;
import com.iturtle.spring.web.logging.AccessLogEntry;
import com.iturtle.spring.web.logging.AccessLogProperties;
import com.iturtle.spring.web.logging.AccessLogWriter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.Map;

/**
 * 서블릿 요청에서 접근 로그 값을 꺼내 AccessLogWriter 로 기록한다.
 * <p>
 * 처리되지 않은 예외로 끝난 요청은 최종 상태 코드가 이후 에러 디스패치(/error)에서 정해지므로, 그 자리에서 기록하지 않고
 * 요청 속성에 보관했다가 에러 디스패치가 끝난 뒤 최종 상태 코드로 기록한다. 그때는 MDC 가 이미 복원되어 있으므로
 * 요청 처리 중의 MDC 값(user.id 등)도 함께 보관한다. 이를 위해 REQUEST / ERROR 디스패치에 모두 등록한다.
 */
public class AccessLogFilter extends OncePerRequestFilter {

    static final String PENDING_ATTRIBUTE = AccessLogFilter.class.getName() + ".pending";

    private final AccessLogWriter writer;
    private final int bodyMaxLength;

    public AccessLogFilter(AccessLogProperties properties) {
        this.writer = new AccessLogWriter(properties);
        this.bodyMaxLength = properties.getBodyMaxLength();
    }

    /**
     * 에러 디스패치는 보관된 요청이 있을 때만 처리한다. 에러 디스패치의 URI 는 /error 이므로 제외 경로 판정은 하지 않는다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            return request.getAttribute(PENDING_ATTRIBUTE) == null;
        }
        return writer.isExcluded(request.getRequestURI());
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            doFilterErrorDispatch(request, response, filterChain);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = request instanceof ContentCachingRequestWrapper wrapper
                ? wrapper
                : new ContentCachingRequestWrapper(request, bodyMaxLength);

        long start = System.nanoTime();
        try {
            filterChain.doFilter(wrappedRequest, response);
        } catch (IOException | ServletException | RuntimeException e) {
            if (response.isCommitted()) {
                // 응답이 이미 나갔으면 에러 디스패치가 일어나지 않는다
                writer.write(entry(wrappedRequest, response.getStatus(), System.nanoTime() - start));
            } else {
                request.setAttribute(PENDING_ATTRIBUTE, new Pending(
                        entry(wrappedRequest, HttpStatus.INTERNAL_SERVER_ERROR.value(), 0), start, MDC.getCopyOfContextMap()));
            }
            throw e;
        }
        writer.write(entry(wrappedRequest, response.getStatus(), System.nanoTime() - start));
    }

    private void doFilterErrorDispatch(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Pending pending = (Pending) request.getAttribute(PENDING_ATTRIBUTE);
        request.removeAttribute(PENDING_ATTRIBUTE);
        try {
            filterChain.doFilter(request, response);
        } finally {
            AccessLogEntry entry = pending.entry();
            try (MdcContext ignored = new MdcContext(pending.mdc()).open()) {
                writer.write(new AccessLogEntry(entry.method(), entry.path(), entry.query(), response.getStatus(),
                        System.nanoTime() - pending.startNanos(), entry.clientIp(), entry.contentType(),
                        entry.characterEncoding(), entry.body(), entry.contentLength()));
            }
        }
    }

    private AccessLogEntry entry(ContentCachingRequestWrapper request, int status, long durationNanos) {
        return new AccessLogEntry(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                status,
                durationNanos,
                request.getRemoteAddr(),
                request.getContentType(),
                request.getCharacterEncoding(),
                request.getContentAsByteArray(),
                request.getContentLengthLong()
        );
    }

    /**
     * 에러 디스패치까지 미뤄 둔 접근 로그. status / duration 은 에러 디스패치가 끝난 뒤 채운다.
     */
    private record Pending(AccessLogEntry entry, long startNanos, Map<String, String> mdc) {
    }
}
