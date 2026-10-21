package com.iturtle.spring.webmvc.logging;

import com.iturtle.spring.core.mdc.MdcContext;
import com.iturtle.spring.web.logging.WebMdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 요청마다 기본값(HTTP 메서드, 경로)과 MdcContributor 들의 값을 MDC 에 반영하고, 요청이 끝나면 이전 상태로 복원한다.
 */
public class MdcFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_ATTRIBUTE = MdcFilter.class.getName() + ".traceId";

    private final List<MdcContributor> contributors;

    public MdcFilter(List<MdcContributor> contributors) {
        this.contributors = List.copyOf(contributors);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 에러 디스패치(/error) 시점에는 MDC 가 이미 복원되어 있으므로 traceId 를 요청 속성에도 남긴다
        String traceId = MDC.get(WebMdcKeys.TRACE_ID);
        if (traceId != null) {
            request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        }
        try (MdcContext ignored = createContext(request).open()) {
            filterChain.doFilter(request, response);
        }
    }

    private MdcContext createContext(HttpServletRequest request) {
        MdcContext context = new MdcContext()
                .put(WebMdcKeys.HTTP_REQUEST_METHOD, request.getMethod())
                .put(WebMdcKeys.URL_PATH, request.getRequestURI());
        contributors.forEach(contributor ->
                context.putAll(contributor.contribute(request))
        );
        return context;
    }
}
