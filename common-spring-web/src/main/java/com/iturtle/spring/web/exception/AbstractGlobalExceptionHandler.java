package com.iturtle.spring.web.exception;

import com.iturtle.core.exception.BusinessException;
import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.core.exception.ErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.web.dto.response.FieldErrorResponse;
import com.iturtle.spring.web.logging.WebMdcKeys;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * MVC / WebFlux 공통 예외 처리. 각 스택의 구현체가 상속하고 @RestControllerAdvice 를 붙여 스택 전용 예외를 추가한다.
 */
@Slf4j(topic = "com.iturtle.error")
public abstract class AbstractGlobalExceptionHandler {

    private static final boolean JAKARTA_VALIDATION_PRESENT =
            ClassUtils.isPresent("jakarta.validation.ConstraintViolationException",
                    AbstractGlobalExceptionHandler.class.getClassLoader());

    private static final boolean SPRING_SECURITY_PRESENT =
            ClassUtils.isPresent("org.springframework.security.access.AccessDeniedException",
                    AbstractGlobalExceptionHandler.class.getClassLoader());

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
     * RestClient 로 호출한 외부 서비스의 오류 응답 / 연결 실패.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiBaseResponse<Void>> handleRestClientException(RestClientException e) {
        ErrorCode errorCode = CommonErrorCode.EXTERNAL_API_ERROR;
        if (e instanceof RestClientResponseException responseException) {
            // 예외 메시지에 상대 서버의 응답 본문이 포함되므로 상태 코드만 남긴다
            log.error("[{}] 외부 API 오류 응답 {}", errorCode.getCode(), responseException.getStatusCode());
        } else {
            log.error("[{}] {}", errorCode.getCode(), e.getMessage(), e);
        }
        return toResponse(errorCode);
    }

    /**
     * NoResourceFoundException(404), HttpMediaTypeNotSupportedException(415) 처럼 상태 코드를 가진
     * Spring 예외(ErrorResponse)는 그 상태 코드로 응답한다.
     * 서비스 계층 @Validated 검증 실패(ConstraintViolationException)는 400,
     * 컨트롤러 안의 Security 예외(@PreAuthorize 거부 등)는 401 / 403 으로 응답한다. 그 외에는 500 이다.
     * <p>
     * jakarta.validation / Spring Security 가 없는 서비스에서도 이 클래스가 로딩되도록
     * 별도 핸들러 메서드 대신 여기서 확인한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiBaseResponse<Void>> handleException(Exception e) {
        if (SPRING_SECURITY_PRESENT) {
            ErrorCode securityErrorCode = SecurityExceptions.resolve(e);
            if (securityErrorCode != null) {
                return handleClientError(securityErrorCode, e);
            }
        }
        if (e instanceof ErrorResponse errorResponse && !errorResponse.getStatusCode().is5xxServerError()) {
            return handleClientError(resolveErrorCode(errorResponse.getStatusCode()), e);
        }
        if (JAKARTA_VALIDATION_PRESENT && ConstraintViolations.matches(e)) {
            return handleValidationError(ConstraintViolations.toFieldErrors(e));
        }
        log.error("[{}] {}", CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(), e.getMessage(), e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 같은 상태 코드의 CommonErrorCode 를 찾는다. 없으면 INVALID_INPUT(400) 으로 응답한다.
     */
    protected ErrorCode resolveErrorCode(HttpStatusCode status) {
        return CommonErrorCode.fromStatus(status.value());
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
        return toResponse(errorCode, errors);
    }

    protected ResponseEntity<ApiBaseResponse<Void>> toResponse(ErrorCode errorCode) {
        return toResponse(errorCode, null);
    }

    protected ResponseEntity<ApiBaseResponse<Void>> toResponse(ErrorCode errorCode, List<FieldErrorResponse> errors) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiBaseResponse.fail(
                        new com.iturtle.spring.web.dto.response.ErrorResponse(errorCode, errors, traceId())));
    }

    /**
     * 응답에 담을 traceId. MDC 를 쓰지 않는 스택(WebFlux 등)은 오버라이드한다.
     */
    protected String traceId() {
        return MDC.get(WebMdcKeys.TRACE_ID);
    }

    /**
     * jakarta.validation 타입을 이 클래스에만 두어, 라이브러리가 없으면 로딩되지 않게 한다.
     */
    private static final class ConstraintViolations {

        static boolean matches(Exception e) {
            return e instanceof ConstraintViolationException;
        }

        static List<FieldErrorResponse> toFieldErrors(Exception e) {
            return ((ConstraintViolationException) e).getConstraintViolations().stream()
                    .map(violation -> new FieldErrorResponse(field(violation.getPropertyPath()), violation.getMessage()))
                    .toList();
        }

        /**
         * 메서드 검증이면 경로가 "메서드.파라미터.필드" 이므로 메서드 이름을 뺀다.
         */
        private static String field(Path path) {
            return StreamSupport.stream(path.spliterator(), false)
                    .filter(node -> node.getKind() != ElementKind.METHOD && node.getKind() != ElementKind.CONSTRUCTOR)
                    .map(Path.Node::toString)
                    .collect(Collectors.joining("."));
        }
    }

    /**
     * Spring Security 타입을 이 클래스에만 두어, 라이브러리가 없으면 로딩되지 않게 한다.
     * ExceptionTranslationFilter 와 같은 기준으로 판단한다: 인증 실패이거나, 권한 거부인데 로그인하지 않은
     * (익명 / remember-me) 사용자면 401, 로그인한 사용자의 권한 거부는 403.
     * <p>
     * SecurityContextHolder 기반이므로 WebFlux 구현에서는 ReactiveSecurityContextHolder 로 판단해야 한다.
     */
    private static final class SecurityExceptions {

        private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

        static ErrorCode resolve(Exception e) {
            if (e instanceof AuthenticationException) {
                return CommonErrorCode.UNAUTHORIZED;
            }
            if (e instanceof AccessDeniedException) {
                return isAuthenticated() ? CommonErrorCode.FORBIDDEN : CommonErrorCode.UNAUTHORIZED;
            }
            return null;
        }

        private static boolean isAuthenticated() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null
                    && !TRUST_RESOLVER.isAnonymous(authentication)
                    && !TRUST_RESOLVER.isRememberMe(authentication);
        }
    }

}
