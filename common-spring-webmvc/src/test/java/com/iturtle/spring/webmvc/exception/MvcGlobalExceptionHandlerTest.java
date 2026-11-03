package com.iturtle.spring.webmvc.exception;

import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.web.dto.response.FieldErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MvcGlobalExceptionHandlerTest {

    private final MvcGlobalExceptionHandler handler = new MvcGlobalExceptionHandler();

    @Test
    void noResourceFoundMappedTo404() {
        // 생성자가 Spring 6 과 7 에서 다르므로 mock 으로 만든다. 핸들러는 ErrorResponse 의 상태 코드만 본다
        NoResourceFoundException exception = mock(NoResourceFoundException.class);
        when(exception.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);

        assertError(handler.handleException(exception), CommonErrorCode.NOT_FOUND);
    }

    @Test
    void errorResponseStatusMappedToCommonErrorCode() {
        assertError(handler.handleException(new HttpMediaTypeNotSupportedException("text/xml")),
                CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertError(handler.handleException(new MaxUploadSizeExceededException(1024)),
                CommonErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void unmappedClientStatusFallsBackToInvalidInput() {
        assertError(handler.handleException(new ErrorResponseException(HttpStatus.REQUEST_TIMEOUT)),
                CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void serverErrorResponseAndUnknownExceptionMappedTo500() {
        assertError(handler.handleException(new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE)),
                CommonErrorCode.INTERNAL_SERVER_ERROR);
        assertError(handler.handleException(new IllegalStateException("boom")),
                CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    void bindExceptionIncludesFieldErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));

        ResponseEntity<ApiBaseResponse<Void>> response = handler.handleBindException(new BindException(bindingResult));

        assertError(response, CommonErrorCode.INVALID_INPUT);
        assertEquals(List.of(new FieldErrorResponse("email", "must not be blank")),
                response.getBody().error().errors());
    }

    @Test
    void nonValidationErrorHasNoFieldErrors() {
        assertNull(handler.handleException(new IllegalStateException()).getBody().error().errors());
    }

    private void assertError(ResponseEntity<ApiBaseResponse<Void>> response, CommonErrorCode expected) {
        assertEquals(expected.getStatus(), response.getStatusCode().value());
        assertFalse(response.getBody().success());
        assertEquals(expected.getCode(), response.getBody().error().code());
    }
}
