package com.iturtle.spring.webmvc.boot3;

import com.iturtle.spring.web.client.ClientLogInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 3 에서만 켜지는 설정. Boot 4 에서 패키지가 옮겨진 타입을 쓰는 빈을 등록한다.
 * <ul>
 *     <li>에러 컨트롤러(/error): ErrorController 타입 빈을 등록하면 대체된다</li>
 *     <li>외부 호출 로그: iturtle.web.client-log.enabled=false 로 끈다. Spring Boot 의 RestClient.Builder 로 만든 RestClient 에만 적용된다</li>
 * </ul>
 * BasicErrorController 보다 먼저 ErrorController 를 등록하기 위해 ErrorMvcAutoConfiguration 보다 먼저 실행된다.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.boot.web.servlet.error.ErrorController")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class Boot3WebMvcAutoConfiguration {

    // 반환 타입이 ErrorController 여야 BasicErrorController 의 @ConditionalOnMissingBean 이 이 빈을 찾는다
    @Bean
    @ConditionalOnMissingBean(ErrorController.class)
    public Boot3MvcErrorController mvcErrorController(ObjectProvider<ErrorAttributes> errorAttributes) {
        return new Boot3MvcErrorController(errorAttributes.getIfAvailable(DefaultErrorAttributes::new));
    }

    @Bean
    @ConditionalOnMissingBean(name = "clientLogRestClientCustomizer")
    @ConditionalOnProperty(prefix = "iturtle.web.client-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RestClientCustomizer clientLogRestClientCustomizer() {
        ClientLogInterceptor interceptor = new ClientLogInterceptor();
        return builder -> builder.requestInterceptor(interceptor);
    }
}
