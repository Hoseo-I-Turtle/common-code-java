package com.iturtle.spring.webmvc.boot3;

import com.iturtle.spring.webmvc.exception.MvcErrorController;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.web.context.request.WebRequest;

/**
 * Spring Boot 3 용 MvcErrorController. Boot 의 ErrorController 타입이어야 BasicErrorController 가 등록되지 않는다.
 */
public class Boot3MvcErrorController extends MvcErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public Boot3MvcErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @Override
    protected Throwable getError(WebRequest request) {
        return errorAttributes.getError(request);
    }
}
