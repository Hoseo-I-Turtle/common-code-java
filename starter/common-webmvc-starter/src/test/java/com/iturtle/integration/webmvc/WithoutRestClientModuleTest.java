package com.iturtle.integration.webmvc;

import com.iturtle.spring.webmvc.exception.MvcErrorController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 4 는 RestClient 지원이 별도 모듈이다. 모듈이 없는 서비스에서도 자동 설정이 실패하지 않는지 검증한다.
 * 두 Boot 버전의 자동 설정을 함께 넣어 해당 버전의 것만 켜지는지도 함께 확인한다.
 */
class WithoutRestClientModuleTest {

    @Test
    void autoConfigurationWorksWithoutRestClientModule() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.restclient"))
                .withConfiguration(AutoConfigurations.of(
                        ClassUtils.resolveClassName("com.iturtle.spring.webmvc.boot3.Boot3WebMvcAutoConfiguration", null),
                        ClassUtils.resolveClassName("com.iturtle.spring.webmvc.boot4.Boot4WebMvcAutoConfiguration", null)))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(MvcErrorController.class).size());
                });
    }
}
