package com.iturtle.spring.web.logging;

/**
 * 웹 요청 공통 MDC 키 (ECS 필드 이름). MVC / WebFlux 구현이 같은 키를 사용한다.
 */
public final class WebMdcKeys {

    public static final String HTTP_REQUEST_METHOD = "http.request.method";
    public static final String URL_PATH = "url.path";

    /** Micrometer Tracing 이 MDC 에 넣는 키 */
    public static final String TRACE_ID = "traceId";

    private WebMdcKeys() {}

}
