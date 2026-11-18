package com.iturtle.spring.security.webmvc;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.DeferredCsrfToken;

import static org.junit.jupiter.api.Assertions.*;

class CsrfCookieFilterTest {

    private final CsrfCookieFilter filter = new CsrfCookieFilter();

    @Test
    void deferredTokenWrittenAsCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // CsrfFilter 가 하는 일: 지연 토큰을 요청 속성에 둔다 (아직 쿠키를 쓰지 않음)
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        DeferredCsrfToken deferred = repository.loadDeferredToken(request, response);
        new CsrfTokenRequestAttributeHandler().handle(request, response, deferred::get);
        assertNull(response.getCookie("XSRF-TOKEN"));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        assertFalse(cookie.isHttpOnly());
        assertEquals(((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken(), cookie.getValue());
        assertSame(request, chain.getRequest());
    }

    @Test
    void passesThroughWithoutCsrf() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(response.getCookie("XSRF-TOKEN"));
        assertSame(request, chain.getRequest());
    }
}
