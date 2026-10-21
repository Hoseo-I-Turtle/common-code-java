package com.iturtle.spring.webmvc.logging;

import com.iturtle.spring.web.logging.WebMdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MdcFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void contributorValuesAppliedDuringRequestAndRestoredAfter() throws Exception {
        MDC.put("traceId", "t-1");
        MdcFilter filter = new MdcFilter(List.of(
                request -> Map.of("user.id", request.getHeader("X-User-Id")),
                request -> Map.of("tenant.id", "acme")
        ));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-User-Id", "u-1");
        Map<String, String> seen = new HashMap<>();

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.putAll(MDC.getCopyOfContextMap());
            }
        });

        assertEquals("GET", seen.get(WebMdcKeys.HTTP_REQUEST_METHOD));
        assertEquals("/api/orders", seen.get(WebMdcKeys.URL_PATH));
        assertEquals("u-1", seen.get("user.id"));
        assertEquals("acme", seen.get("tenant.id"));
        assertEquals("t-1", seen.get("traceId"));
        assertNull(MDC.get(WebMdcKeys.URL_PATH));
        assertNull(MDC.get("user.id"));
        assertNull(MDC.get("tenant.id"));
        assertEquals("t-1", MDC.get("traceId"));
    }
}
