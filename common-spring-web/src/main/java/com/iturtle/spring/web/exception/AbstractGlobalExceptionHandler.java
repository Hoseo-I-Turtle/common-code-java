package com.iturtle.spring.web.exception;

import com.iturtle.core.exception.BusinessException;
import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.core.exception.ErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiBaseResponse<Void>> handleException(Exception e) {
        log.error("[{}] {}", CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(), e.getMessage(), e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 클라이언트 오류(4xx)로 처리할 스택 전용 예외에서 사용한다.
     */
    protected ResponseEntity<ApiBaseResponse<Void>> handleClientError(ErrorCode errorCode, Exception e) {
        log.warn("[{}] {}", errorCode.getCode(), e.getMessage());
        return toResponse(errorCode);
    }

    protected ResponseEntity<ApiBaseResponse<Void>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiBaseResponse.fail(errorCode));
    }

}
