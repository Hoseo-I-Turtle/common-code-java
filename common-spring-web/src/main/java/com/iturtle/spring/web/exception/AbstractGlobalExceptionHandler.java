package com.iturtle.spring.web.exception;

import com.iturtle.core.exception.BusinessException;
import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.core.exception.ErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.web.dto.response.FieldErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Arrays;
import java.util.List;

/**
 * MVC / WebFlux 공통 예외 처리. 각 스택의 구현체가 상속하고 @RestControllerAdvice 를 붙여 스택 전용 예외를 추가한다.
 */
@Slf4j(topic = "com.iturtle.error")
public abstract class AbstractGlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiBaseResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus() >= 500) {
            log.error("[{}] {}", errorCode.getCode(), e.getMessage(), e);
        } else {
            log.warn("[{}] {}", errorCode.getCode(), e.getMessage());
        }
        return toResponse(errorCode);
    }

    /**
     * 컨트롤러 파라미터에 직접 붙인 제약 조건(@Min, @NotBlank 등) 위반.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiBaseResponse<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        List<FieldErrorResponse> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorResponse(
                                error instanceof FieldError fieldError
                                        ? fieldError.getField()
                                        : result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return handleValidationError(errors);
    }

    /**
     * NoResourceFoundException(404), HttpMediaTypeNotSupportedException(415) 처럼 상태 코드를 가진
     * Spring 예외(ErrorResponse)는 그 상태 코드로 응답한다. 그 외에는 500 이다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiBaseResponse<Void>> handleException(Exception e) {
        if (e instanceof ErrorResponse errorResponse && !errorResponse.getStatusCode().is5xxServerError()) {
            return handleClientError(resolveErrorCode(errorResponse.getStatusCode()), e);
        }
        log.error("[{}] {}", CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(), e.getMessage(), e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 같은 상태 코드의 CommonErrorCode 를 찾는다. 없으면 INVALID_INPUT(400) 으로 응답한다.
     */
    protected ErrorCode resolveErrorCode(HttpStatusCode status) {
        return Arrays.stream(CommonErrorCode.values())
                .filter(errorCode -> errorCode.getStatus() == status.value())
                .findFirst()
                .orElse(CommonErrorCode.INVALID_INPUT);
    }

    /**
     * 클라이언트 오류(4xx)로 처리할 스택 전용 예외에서 사용한다.
     */
    protected ResponseEntity<ApiBaseResponse<Void>> handleClientError(ErrorCode errorCode, Exception e) {
        // 없는 경로를 찌르는 스캔 요청이 많아 404 는 debug 로 남긴다
        if (errorCode.getStatus() == 404) {
            log.debug("[{}] {}", errorCode.getCode(), e.getMessage());
        } else {
            log.warn("[{}] {}", errorCode.getCode(), e.getMessage());
        }
        return toResponse(errorCode);
    }

    protected ResponseEntity<ApiBaseResponse<Void>> handleValidationError(BindingResult bindingResult) {
        List<FieldErrorResponse> errors = bindingResult.getAllErrors().stream()
                .map(error -> new FieldErrorResponse(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return handleValidationError(errors);
    }

    protected ResponseEntity<ApiBaseResponse<Void>> handleValidationError(List<FieldErrorResponse> errors) {
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
        log.warn("[{}] {}", errorCode.getCode(), errors);
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiBaseResponse.fail(errorCode, errors));
    }

    protected ResponseEntity<ApiBaseResponse<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiBaseResponse.fail(errorCode));
    }

}
