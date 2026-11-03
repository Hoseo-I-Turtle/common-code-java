package com.iturtle.integration.security;

import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.security.webmvc.SecurityErrorResponseWriter;
import com.iturtle.spring.security.webmvc.autoconfigure.CommonSecurityWebMvcAutoConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 4 기본 구성(Jackson 3 만 있음)을 검증한다. Jackson 2 가 없는 클래스패스에서 testJackson3Only 태스크로만 돌린다.
 */
@Tag("jackson3-only")
class Jackson3OnlyAutoConfigurationTest {

    @Test
    void writesErrorBodyWithJackson3() {
        assertFalse(ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", null));

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CommonSecurityWebMvcAutoConfiguration.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    context.getBean(SecurityErrorResponseWriter.class)
                            .write(response, CommonErrorCode.FORBIDDEN, new IllegalStateException("denied"));

                    assertEquals(403, response.getStatus());
                    String body = response.getContentAsString();
                    assertTrue(body.contains("\"code\":\"" + CommonErrorCode.FORBIDDEN.getCode() + "\""), body);
                    assertTrue(body.contains("\"success\":false"), body);
                    // ErrorResponse 의 @JsonInclude(NON_NULL) 이 Jackson 3 에서도 적용된다
                    assertFalse(body.contains("\"errors\""), body);
                });
    }
}
