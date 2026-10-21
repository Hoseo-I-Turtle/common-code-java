package com.iturtle.spring.security.webmvc;

import com.iturtle.core.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 인증이 필요한 요청에 인증 정보가 없거나 잘못되면 401 C002 로 응답한다.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter writer;

    public ApiAuthenticationEntryPoint(SecurityErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        writer.write(response, CommonErrorCode.UNAUTHORIZED, e);
    }
}
