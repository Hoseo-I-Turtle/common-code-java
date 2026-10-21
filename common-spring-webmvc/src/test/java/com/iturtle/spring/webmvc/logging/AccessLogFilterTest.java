package com.iturtle.spring.webmvc.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import com.iturtle.spring.web.logging.AccessLogProperties;
import com.iturtle.spring.web.logging.WebMdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter(new AccessLogProperties());
    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("com.iturtle.access");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        accessLogger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void methodAndPathNotDuplicatedWhenAlreadyInMdc() throws Exception {
        MDC.put(WebMdcKeys.HTTP_REQUEST_METHOD, "GET");
        MDC.put(WebMdcKeys.URL_PATH, "/api/orders");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        Map<String, Object> fields = singleEventFields();
        assertFalse(fields.containsKey(WebMdcKeys.HTTP_REQUEST_METHOD));
        assertFalse(fields.containsKey(WebMdcKeys.URL_PATH));
        assertEquals("/api/orders", appender.list.get(0).getMDCPropertyMap().get(WebMdcKeys.URL_PATH));
    }

    @Test
    void errorResponseLogsMaskedBodyAndQuery() throws Exception {
        MockHttpServletRequest request = jsonRequest("/api/orders",
                "{\"productId\":77,\"password\":\"p@ss\",\"card\":{\"cardNumber\":1234567890123456}}");
        request.setQueryString("page=1&token=abc");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).setStatus(400);
        });

        Map<String, Object> fields = singleEventFields();
        assertEquals("POST", fields.get("http.request.method"));
        assertEquals("/api/orders", fields.get("url.path"));
        assertEquals(400, fields.get("http.response.status_code"));
        assertEquals("page=1&token=****", fields.get("url.query"));
        assertEquals("{\"productId\":77,\"password\":\"****\",\"card\":{\"cardNumber\":****}}",
                fields.get("http.request.body.content"));
        assertTrue((Long) fields.get("event.duration") >= 0);
    }

    @Test
    void successResponseDoesNotLogBody() throws Exception {
        MockHttpServletRequest request = jsonRequest("/api/orders", "{\"productId\":77}");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> req.getInputStream().readAllBytes());

        Map<String, Object> fields = singleEventFields();
        assertEquals(200, fields.get("http.response.status_code"));
        assertFalse(fields.containsKey("http.request.body.content"));
    }

    @Test
    void uncaughtExceptionLoggedAs500() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(IllegalStateException.class, () ->
                filter.doFilter(request, response, (req, res) -> {
                    throw new IllegalStateException("boom");
                }));

        // 최종 상태는 에러 디스패치에서 정해지므로 아직 기록하지 않는다
        assertTrue(appender.list.isEmpty());

        // 서블릿 컨테이너가 같은 요청으로 /error 를 디스패치한다
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(500));

        assertEquals(500, singleEventFields().get("http.response.status_code"));
    }

    @Test
    void exceptionLoggedWithFinalStatusAndMdcFromErrorDispatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("user.id", "user-1");
        assertThrows(IllegalStateException.class, () ->
                filter.doFilter(request, response, (req, res) -> {
                    throw new IllegalStateException("business conflict");
                }));
        MDC.clear();   // 에러 디스패치 시점에는 MdcFilter 가 MDC 를 이미 복원했다

        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(409));

        assertEquals(409, singleEventFields().get("http.response.status_code"));
        assertEquals("user-1", appender.list.get(0).getMDCPropertyMap().get("user.id"));
        assertNull(MDC.get("user.id"));
    }

    @Test
    void errorDispatchWithoutPendingNotLoggedTwice() throws Exception {
        // sendError 는 요청 단계에서 이미 기록되므로 이후 에러 디스패치에서 다시 기록하지 않는다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).sendError(403));

        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals(403, singleEventFields().get("http.response.status_code"));
    }

    @Test
    void excludedPathNotLogged() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
        });

        assertTrue(appender.list.isEmpty());
    }

    @Test
    void additionalMaskedFieldsAppendedToDefaults() throws Exception {
        AccessLogProperties properties = new AccessLogProperties();
        properties.setAdditionalMaskedFields(List.of("email"));
        AccessLogFilter customFilter = new AccessLogFilter(properties);
        MockHttpServletRequest request = jsonRequest("/api/users", "{\"email\":\"a@b.com\",\"password\":\"p@ss\"}");

        customFilter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            req.getInputStream().readAllBytes();
            ((HttpServletResponse) res).setStatus(400);
        });

        assertEquals("{\"email\":\"****\",\"password\":\"****\"}", singleEventFields().get("http.request.body.content"));
    }

    @Test
    void additionalExcludePathsAppendedToDefaults() throws Exception {
        AccessLogProperties properties = new AccessLogProperties();
        properties.setAdditionalExcludePaths(List.of("/internal/**"));
        AccessLogFilter customFilter = new AccessLogFilter(properties);

        customFilter.doFilter(new MockHttpServletRequest("GET", "/internal/ping"), new MockHttpServletResponse(), (req, res) -> {
        });
        customFilter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), new MockHttpServletResponse(), (req, res) -> {
        });

        assertTrue(appender.list.isEmpty());
    }

    @Test
    void slowRequestLoggedAsWarn() throws Exception {
        AccessLogProperties properties = new AccessLogProperties();
        properties.setSlowThreshold(Duration.ZERO);
        AccessLogFilter customFilter = new AccessLogFilter(properties);

        customFilter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), new MockHttpServletResponse(), (req, res) -> {
        });

        assertEquals(Level.WARN, appender.list.get(0).getLevel());
    }

    @Test
    void normalRequestLoggedAsInfo() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/orders"), new MockHttpServletResponse(), (req, res) -> {
        });

        assertEquals(Level.INFO, appender.list.get(0).getLevel());
    }

    private MockHttpServletRequest jsonRequest(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContentType("application/json");
        request.setCharacterEncoding("UTF-8");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private Map<String, Object> singleEventFields() {
        assertEquals(1, appender.list.size());
        Map<String, Object> fields = new HashMap<>();
        for (KeyValuePair pair : appender.list.get(0).getKeyValuePairs()) {
            fields.put(pair.key, pair.value);
        }
        return fields;
    }
}
