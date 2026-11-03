package com.iturtle.spring.webmvc.boot4;

import com.iturtle.spring.webmvc.exception.MvcErrorController;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.web.context.request.WebRequest;

/**
 * Spring Boot 4 용 MvcErrorController. Boot 의 ErrorController 타입이어야 BasicErrorController 가 등록되지 않는다.
 */
public class Boot4MvcErrorController extends MvcErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public Boot4MvcErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @Override
    protected Throwable getError(WebRequest request) {
        return errorAttributes.getError(request);
    }
}
