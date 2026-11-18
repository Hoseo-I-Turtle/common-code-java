package com.iturtle.spring.security.webmvc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 세션 · 쿠키 방식의 SPA(또는 JS 를 쓰는 화면, Swagger UI)를 위해 CSRF 토큰을 요청마다 미리 만들어
 * {@code XSRF-TOKEN} 쿠키가 항상 내려가게 한다.
 * <p>
 * Spring Security 는 토큰을 쓸 때 만들기 때문에(지연 생성), GET 만 하는 화면에서는 쿠키가 내려가지 않아
 * 다음 POST 가 403 이 될 수 있다. 이 필터가 토큰을 한 번 읽어 저장소(CookieCsrfTokenRepository)가 쿠키를 쓰게 한다.
 * JWT 처럼 CSRF 를 끈 서비스에는 필요 없다.
 * <p>
 * Security 체인 안에서만 동작해야 하므로 <b>빈으로 등록하지 않는다</b> (Filter 빈은 Spring Boot 가 서블릿 필터로도 등록해
 * 체인 밖에서 한 번 더 실행된다). {@code CsrfFilter} 뒤에 직접 넣는다.
 * <pre>
 * http
 *     .csrf(csrf -&gt; csrf
 *             .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
 *             .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))   // Security 7 은 .csrf(CsrfConfigurer::spa)
 *     .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
 * </pre>
 * 브라우저 JS 는 {@code XSRF-TOKEN} 쿠키 값을 {@code X-XSRF-TOKEN} 헤더로 보낸다.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // 지연 토큰을 읽어 저장소가 쿠키를 쓰게 한다
            csrfToken.getToken();
        }
        chain.doFilter(request, response);
    }
}
