package com.iturtle.spring.security.webmvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iturtle.core.exception.ErrorCode;
import com.iturtle.spring.security.jwt.JwtErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.web.dto.response.ErrorResponse;
import com.iturtle.spring.web.logging.WebMdcKeys;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security 필터 단계의 오류를 ApiBaseResponse JSON 으로 직접 쓴다. 필터 단계는 @RestControllerAdvice 를 거치지 않는다.
 */
@Slf4j(topic = "com.iturtle.error")
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode, Exception e) throws IOException {
        // 토큰 만료는 정상적인 수명 주기라 debug. 위조(J002) / 무효화된 토큰 재사용(J003)은 공격 신호일 수 있어 warn
        if (errorCode == JwtErrorCode.EXPIRED_TOKEN) {
            log.debug("[{}] {}", errorCode.getCode(), e.getMessage());
        } else {
            log.warn("[{}] {}", errorCode.getCode(), e.getMessage());
        }
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiBaseResponse<Void> body = ApiBaseResponse.fail(new ErrorResponse(errorCode, null, MDC.get(WebMdcKeys.TRACE_ID)));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
