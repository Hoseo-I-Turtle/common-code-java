package com.iturtle.spring.webmvc.exception;

import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.web.exception.AbstractGlobalExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * BusinessException / Exception 처리는 AbstractGlobalExceptionHandler 에서 상속하고, 서블릿 전용 예외를 추가한다.
 */
@RestControllerAdvice
public class MvcGlobalExceptionHandler extends AbstractGlobalExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiBaseResponse<Void>> handleInvalidInput(Exception e) {
        return handleClientError(CommonErrorCode.INVALID_INPUT, e);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiBaseResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return handleClientError(CommonErrorCode.METHOD_NOT_ALLOWED, e);
    }

}
