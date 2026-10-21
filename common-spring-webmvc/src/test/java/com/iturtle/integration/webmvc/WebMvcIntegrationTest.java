package com.iturtle.integration.webmvc;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iturtle.core.exception.BusinessException;
import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.webmvc.exception.MvcErrorController;
import com.iturtle.spring.webmvc.exception.MvcGlobalExceptionHandler;
import jakarta.servlet.Filter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 서비스와 같은 조건(라이브러리 패키지를 component scan 하지 않고 자동 설정만으로 등록)에서 webmvc 모듈을 검증한다.
 */
@SpringBootTest(classes = WebMvcIntegrationTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebMvcIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger clientLogger = (Logger) LoggerFactory.getLogger("com.iturtle.client");
    private final ListAppender<ILoggingEvent> clientLogs = new ListAppender<>();
    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("com.iturtle.access");
    private final ListAppender<ILoggingEvent> accessLogs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        clientLogs.start();
        clientLogger.addAppender(clientLogs);
        accessLogs.start();
        accessLogger.addAppender(accessLogs);
    }

    @AfterEach
    void tearDown() {
        clientLogger.detachAppender(clientLogs);
        accessLogger.detachAppender(accessLogs);
    }

    @Test
    void autoConfiguredBeansReplaceBootDefaults() {
        assertEquals(1, context.getBeansOfType(MvcGlobalExceptionHandler.class).size());
        assertEquals(1, context.getBeansOfType(MvcErrorController.class).size());
        assertTrue(context.getBeansOfType(BasicErrorController.class).isEmpty());
    }

    @Test
    void businessExceptionInController() throws Exception {
        assertError(restTemplate.getForEntity("/test/business", String.class), 409, CommonErrorCode.CONFLICT);
    }

    @Test
    void unknownPathIs404() throws Exception {
        assertError(restTemplate.getForEntity("/unknown/path", String.class), 404, CommonErrorCode.NOT_FOUND);
    }

    @Test
    void validationErrorIncludesFieldErrors() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/test/valid", HttpMethod.POST, new HttpEntity<>("{\"email\":\"\"}", headers), String.class);

        JsonNode body = assertError(response, 400, CommonErrorCode.INVALID_INPUT);
        assertEquals("email", body.get("error").get("errors").get(0).get("field").asText());
    }

    @Test
    void exceptionFromFilterGoesThroughErrorController() throws Exception {
        // 컨트롤러 밖(필터)에서 던진 예외 -> 서블릿 컨테이너 -> /error -> MvcErrorController
        assertError(restTemplate.getForEntity("/test/filter-error", String.class), 409, CommonErrorCode.CONFLICT);

        // 접근 로그도 500 이 아니라 /error 가 정한 최종 상태로, 한 번만 기록된다
        List<String> logs = accessLogs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("GET /test/filter-error ")).toList();
        assertEquals(1, logs.size(), logs.toString());
        assertTrue(logs.get(0).startsWith("GET /test/filter-error 409 "), logs.get(0));
    }

    @Test
    void sendErrorGoesThroughErrorController() throws Exception {
        assertError(restTemplate.getForEntity("/test/send-error", String.class), 403, CommonErrorCode.FORBIDDEN);
    }

    @Test
    void restClientFromBootBuilderLogsCalls() {
        String body = restClientBuilder.baseUrl("http://localhost:" + port).build()
                .get().uri("/test/ok?token=secret").retrieve().body(String.class);

        assertNotNull(body);
        assertTrue(clientLogs.list.stream().anyMatch(event ->
                event.getFormattedMessage().contains("GET http://localhost:" + port + "/test/ok 200")));
        assertTrue(clientLogs.list.stream().noneMatch(event -> event.getFormattedMessage().contains("secret")));
    }

    @Test
    void httpClientDefaultTimeoutsApplied() {
        assertEquals("3s", environment.getProperty("spring.http.client.connect-timeout"));
        assertEquals("10s", environment.getProperty("spring.http.client.read-timeout"));
    }

    private JsonNode assertError(ResponseEntity<String> response, int status, CommonErrorCode errorCode) throws Exception {
        assertEquals(status, response.getStatusCode().value(), response.getBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertFalse(body.get("success").asBoolean());
        assertEquals(errorCode.getCode(), body.get("error").get("code").asText());
        return body;
    }

    // 테스트 클래스에 중첩된 클래스는 component scan 에서 제외되므로 직접 등록한다
    @SpringBootApplication
    @Import(TestController.class)
    static class TestApplication {

        @Bean
        FilterRegistrationBean<Filter> throwingFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
                throw new BusinessException(CommonErrorCode.CONFLICT);
            });
            registration.addUrlPatterns("/test/filter-error");
            return registration;
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/test/ok")
        ApiBaseResponse<String> ok() {
            return ApiBaseResponse.success("ok");
        }

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }

        @PostMapping("/test/valid")
        void valid(@Valid @RequestBody SignUpRequest request) {
        }

        @GetMapping("/test/send-error")
        void sendError(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            response.sendError(403);
        }
    }

    record SignUpRequest(@NotBlank String email) {
    }
}
