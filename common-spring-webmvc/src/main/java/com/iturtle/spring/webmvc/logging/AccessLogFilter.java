package com.iturtle.spring.webmvc.logging;

import com.iturtle.spring.web.logging.AccessLogEntry;
import com.iturtle.spring.web.logging.AccessLogProperties;
import com.iturtle.spring.web.logging.AccessLogWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 서블릿 요청에서 접근 로그 값을 꺼내 AccessLogWriter 로 기록한다.
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private final AccessLogWriter writer;
    private final int bodyMaxLength;

    public AccessLogFilter(AccessLogProperties properties) {
        this.writer = new AccessLogWriter(properties);
        this.bodyMaxLength = properties.getBodyMaxLength();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return writer.isExcluded(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = request instanceof ContentCachingRequestWrapper wrapper
                ? wrapper
                : new ContentCachingRequestWrapper(request, bodyMaxLength);

        long start = System.nanoTime();
        boolean failed = false;
        try {
            filterChain.doFilter(wrappedRequest, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            int status = failed ? HttpStatus.INTERNAL_SERVER_ERROR.value() : response.getStatus();
            writer.write(new AccessLogEntry(
                    wrappedRequest.getMethod(),
                    wrappedRequest.getRequestURI(),
                    wrappedRequest.getQueryString(),
                    status,
                    System.nanoTime() - start,
                    wrappedRequest.getRemoteAddr(),
                    wrappedRequest.getContentType(),
                    wrappedRequest.getCharacterEncoding(),
                    wrappedRequest.getContentAsByteArray(),
                    wrappedRequest.getContentLengthLong()
            ));
        }
    }
}
