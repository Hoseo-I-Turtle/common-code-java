package com.iturtle.spring.webmvc.exception;

import com.iturtle.core.exception.BusinessException;
import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.core.exception.ErrorCode;
import com.iturtle.spring.web.dto.response.ApiBaseResponse;
import com.iturtle.spring.web.logging.WebMdcKeys;
import com.iturtle.spring.webmvc.logging.MdcFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * 컨트롤러 밖(필터, sendError)에서 발생한 오류도 ApiBaseResponse 로 응답하도록 Spring Boot 의 BasicErrorController 를 대체한다.
 * BusinessException 은 그 ErrorCode, 상태 코드를 가진 Spring 예외는 그 상태 코드, 그 외에는 sendError 상태 코드로 응답한다.
 * <p>
 * 필터에서 던진 예외는 서블릿 컨테이너가 error 로그를 남기므로 여기서는 debug 로만 남긴다.
 */
@Slf4j(topic = "com.iturtle.error")
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class MvcErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public MvcErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping
    public ResponseEntity<ApiBaseResponse<Void>> error(HttpServletRequest request) {
        Throwable error = unwrap(errorAttributes.getError(new ServletWebRequest(request)));
        ErrorCode errorCode = resolveErrorCode(request, error);
        log.debug("[{}] {} {}", errorCode.getCode(), request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI),
                error != null ? error.getMessage() : "");
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiBaseResponse.fail(
                        new com.iturtle.spring.web.dto.response.ErrorResponse(errorCode, null, traceId(request))));
    }

    private ErrorCode resolveErrorCode(HttpServletRequest request, Throwable error) {
        if (error instanceof BusinessException businessException) {
            return businessException.getErrorCode();
        }
        if (error instanceof ErrorResponse errorResponse) {
            return CommonErrorCode.fromStatus(errorResponse.getStatusCode().value());
        }
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return CommonErrorCode.fromStatus(status instanceof Integer code ? code : 500);
    }

    private Throwable unwrap(Throwable error) {
        while (error instanceof ServletException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private String traceId(HttpServletRequest request) {
        String traceId = MDC.get(WebMdcKeys.TRACE_ID);
        return traceId != null ? traceId : (String) request.getAttribute(MdcFilter.TRACE_ID_ATTRIBUTE);
    }
}
