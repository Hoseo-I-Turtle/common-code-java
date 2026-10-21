package com.iturtle.spring.webmvc.exception;

import com.iturtle.core.exception.BusinessException;
import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.webmvc.logging.MdcFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class MvcErrorControllerTest {

    private final MvcErrorController controller = new MvcErrorController(new DefaultErrorAttributes());

    @Test
    void sendErrorStatusMappedToCommonErrorCode() {
        MockHttpServletRequest request = errorRequest(401);

        assertError(controller.error(request), CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void businessExceptionFromFilterUsesItsErrorCode() {
        MockHttpServletRequest request = errorRequest(500);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION,
                new ServletException(new BusinessException(CommonErrorCode.CONFLICT)));

        assertError(controller.error(request), CommonErrorCode.CONFLICT);
    }

    @Test
    void errorResponseExceptionUsesItsStatus() {
        MockHttpServletRequest request = errorRequest(500);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new ErrorResponseException(HttpStatus.NOT_FOUND));

        assertError(controller.error(request), CommonErrorCode.NOT_FOUND);
    }

    @Test
    void unknownExceptionMappedTo500WithTraceIdFromRequestAttribute() {
        MockHttpServletRequest request = errorRequest(500);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("boom"));
        request.setAttribute(MdcFilter.TRACE_ID_ATTRIBUTE, "trace-1");

        ResponseEntity<ApiBaseResponse<Void>> response = controller.error(request);

        assertError(response, CommonErrorCode.INTERNAL_SERVER_ERROR);
        assertEquals("trace-1", response.getBody().error().traceId());
    }

    private MockHttpServletRequest errorRequest(int status) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/orders");
        return request;
    }

    private void assertError(ResponseEntity<ApiBaseResponse<Void>> response, CommonErrorCode expected) {
        assertEquals(expected.getStatus(), response.getStatusCode().value());
        assertEquals(expected.getCode(), response.getBody().error().code());
    }
}
