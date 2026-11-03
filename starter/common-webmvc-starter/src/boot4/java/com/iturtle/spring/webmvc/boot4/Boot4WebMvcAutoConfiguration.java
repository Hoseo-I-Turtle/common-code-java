package com.iturtle.spring.webmvc.boot4;

import com.iturtle.spring.web.client.ClientLogInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 에서만 켜지는 설정. Boot 3 와 패키지가 다른 타입을 쓰는 빈을 등록한다.
 * <ul>
 *     <li>에러 컨트롤러(/error): ErrorController 타입 빈을 등록하면 대체된다</li>
 *     <li>외부 호출 로그: spring-boot-restclient 가 있을 때만 등록한다. iturtle.web.client-log.enabled=false 로 끈다</li>
 * </ul>
 * BasicErrorController 보다 먼저 ErrorController 를 등록하기 위해 ErrorMvcAutoConfiguration 보다 먼저 실행된다.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.boot.webmvc.error.ErrorController")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class Boot4WebMvcAutoConfiguration {

    // 반환 타입이 ErrorController 여야 BasicErrorController 의 @ConditionalOnMissingBean 이 이 빈을 찾는다
    @Bean
    @ConditionalOnMissingBean(ErrorController.class)
    public Boot4MvcErrorController mvcErrorController(ObjectProvider<ErrorAttributes> errorAttributes) {
        return new Boot4MvcErrorController(errorAttributes.getIfAvailable(DefaultErrorAttributes::new));
    }

    // Boot 4 는 RestClient 지원이 별도 모듈이라 없는 서비스도 있다
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.restclient.RestClientCustomizer")
    static class ClientLogConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "clientLogRestClientCustomizer")
        @ConditionalOnProperty(prefix = "iturtle.web.client-log", name = "enabled", havingValue = "true", matchIfMissing = true)
        public RestClientCustomizer clientLogRestClientCustomizer() {
            ClientLogInterceptor interceptor = new ClientLogInterceptor();
            return builder -> builder.requestInterceptor(interceptor);
        }
    }
}
