package com.iturtle.spring.web.exception;

import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.web.dto.response.FieldErrorResponse;
import com.iturtle.spring.web.logging.WebMdcKeys;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbstractGlobalExceptionHandlerTest {

    private final AbstractGlobalExceptionHandler handler = new AbstractGlobalExceptionHandler() {
    };

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void traceIdFromMdcIncluded() {
        MDC.put(WebMdcKeys.TRACE_ID, "trace-1");

        ResponseEntity<ApiBaseResponse<Void>> response = handler.handleException(new IllegalStateException());

        assertEquals("trace-1", response.getBody().error().traceId());
    }

    @Test
    void constraintViolationMappedTo400WithoutMethodName() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        OrderService service = new OrderService();
        var violations = validator.forExecutables().validateParameters(
                service, OrderService.class.getDeclaredMethod("create", String.class), new Object[]{" "});

        ResponseEntity<ApiBaseResponse<Void>> response =
                handler.handleException(new ConstraintViolationException(violations));

        assertEquals(400, response.getStatusCode().value());
        assertEquals(CommonErrorCode.INVALID_INPUT.getCode(), response.getBody().error().code());
        List<FieldErrorResponse> errors = response.getBody().error().errors();
        assertEquals(1, errors.size());
        assertEquals("name", errors.get(0).field());
    }

    @Test
    void restClientExceptionMappedTo502() {
        ResponseEntity<ApiBaseResponse<Void>> responseError = handler.handleRestClientException(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));
        ResponseEntity<ApiBaseResponse<Void>> ioError =
                handler.handleRestClientException(new ResourceAccessException("timeout"));

        assertEquals(502, responseError.getStatusCode().value());
        assertEquals(CommonErrorCode.EXTERNAL_API_ERROR.getCode(), responseError.getBody().error().code());
        assertEquals(502, ioError.getStatusCode().value());
    }

    @Test
    void securityExceptionsMappedLikeExceptionTranslationFilter() {
        assertStatus(handler.handleException(new BadCredentialsException("bad")), 401);

        // 인증 정보 없음 / 익명 사용자의 권한 거부는 로그인이 필요하다는 뜻이므로 401
        assertStatus(handler.handleException(new AccessDeniedException("denied")), 401);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertStatus(handler.handleException(new AccessDeniedException("denied")), 401);

        // 로그인한 사용자의 권한 거부는 403
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", null, AuthorityUtils.NO_AUTHORITIES));
        ResponseEntity<ApiBaseResponse<Void>> response = handler.handleException(new AccessDeniedException("denied"));
        assertStatus(response, 403);
        assertEquals(CommonErrorCode.FORBIDDEN.getCode(), response.getBody().error().code());
    }

    private void assertStatus(ResponseEntity<ApiBaseResponse<Void>> response, int status) {
        assertEquals(status, response.getStatusCode().value());
    }

    static class OrderService {
        void create(@NotBlank String name) {
        }
    }
}
