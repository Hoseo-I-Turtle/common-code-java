package com.iturtle.spring.webmvc.exception;

import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.web.exception.AbstractGlobalExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * BusinessException / Exception 처리는 AbstractGlobalExceptionHandler 에서 상속하고, 서블릿 전용 예외를 추가한다.
 * NoResourceFoundException, HttpRequestMethodNotSupportedException 등 ErrorResponse 를 구현한 예외는
 * 상위 클래스의 handleException 이 상태 코드에 맞춰 처리한다.
 * <p>
 * 잡을 예외를 추가하려면 이 클래스를 상속한다. 상속한 빈이 있으면 자동 설정의 기본 빈은 등록되지 않는다.
 * <pre>
 * &#64;RestControllerAdvice   // 상속되지 않으므로 하위 클래스에도 붙인다
 * public class AppExceptionHandler extends MvcGlobalExceptionHandler {
 *
 *     &#64;ExceptionHandler(DataIntegrityViolationException.class)
 *     public ResponseEntity&lt;ApiBaseResponse&lt;Void&gt;&gt; handleDuplicate(DataIntegrityViolationException e) {
 *         return handleClientError(AppErrorCode.DUPLICATE, e);
 *     }
 * }
 * </pre>
 * 상속하지 않은 별도 advice 를 추가하면 이 클래스의 Exception 핸들러가 먼저 선택되어 호출되지 않을 수 있다.
 */
@RestControllerAdvice
public class MvcGlobalExceptionHandler extends AbstractGlobalExceptionHandler {

    /**
     * {@code @Valid} / {@code @Validated} 검증 실패 (MethodArgumentNotValidException 포함).
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiBaseResponse<Void>> handleBindException(BindException e) {
        return handleValidationError(e.getBindingResult());
    }

    /**
     * ErrorResponse 를 구현하지 않아 상태 코드를 알 수 없는 입력 오류.
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiBaseResponse<Void>> handleInvalidInput(Exception e) {
        return handleClientError(CommonErrorCode.INVALID_INPUT, e);
    }

}
