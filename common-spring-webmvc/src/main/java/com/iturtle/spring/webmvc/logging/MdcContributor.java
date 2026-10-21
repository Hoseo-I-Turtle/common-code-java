package com.iturtle.spring.webmvc.logging;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 요청마다 MDC 에 넣을 값을 제공한다. 사용처에서 빈으로 등록하면 MdcFilter 가 요청 시작 시 호출한다.
 * <pre>
 * &#64;Bean
 * MdcContributor userMdcContributor() {
 *     return request -> Map.of("user.id", currentUserId(request));
 * }
 * </pre>
 */
@FunctionalInterface
public interface MdcContributor {

    Map<String, String> contribute(HttpServletRequest request);
}
