package com.iturtle.spring.webmvc.logging;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 요청마다 MDC 에 넣을 값을 제공한다. 사용처에서 빈으로 등록하면 MdcFilter 가 요청 시작 시 @Order 순서로 호출한다.
 * null 값은 무시되지만 Map.of 는 null 을 허용하지 않으므로, 값이 없을 수 있으면 HashMap 을 사용한다.
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
