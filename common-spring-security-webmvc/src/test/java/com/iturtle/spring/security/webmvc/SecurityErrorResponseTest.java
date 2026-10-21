package com.iturtle.spring.security.webmvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.web.logging.WebMdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.*;

class SecurityErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityErrorResponseWriter writer = new SecurityErrorResponseWriter(objectMapper);

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void entryPointWrites401Json() throws Exception {
        MDC.put(WebMdcKeys.TRACE_ID, "trace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint(writer).commence(new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("Full authentication is required"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertFalse(body.get("success").asBoolean());
        assertEquals(CommonErrorCode.UNAUTHORIZED.getCode(), body.get("error").get("code").asText());
        assertEquals("trace-1", body.get("error").get("traceId").asText());
    }

    @Test
    void accessDeniedHandlerWrites403Json() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler(writer).handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("Access Denied"));

        assertEquals(403, response.getStatus());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(CommonErrorCode.FORBIDDEN.getCode(), body.get("error").get("code").asText());
    }
}
