package com.iturtle.spring.webmvc.autoconfigure;

import com.iturtle.spring.web.client.ClientLogInterceptor;
import com.iturtle.spring.webmvc.logging.AccessLogFilter;
import com.iturtle.spring.web.logging.AccessLogProperties;
import com.iturtle.spring.webmvc.exception.MvcErrorController;
import com.iturtle.spring.webmvc.exception.MvcGlobalExceptionHandler;
import com.iturtle.spring.webmvc.logging.MdcContributor;
import com.iturtle.spring.webmvc.logging.MdcFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.Bean;

/**
 * 서블릿 웹 애플리케이션에 전역 예외 핸들러, 에러 컨트롤러, MDC 필터, 접근 로그 필터, 외부 호출 로그를 등록한다.
 * <ul>
 *     <li>예외 핸들러: MvcGlobalExceptionHandler 타입 빈을 등록하면 대체된다</li>
 *     <li>에러 컨트롤러(/error): ErrorController 타입 빈을 등록하면 대체된다</li>
 *     <li>MDC 필터: mdcFilterRegistration 이름의 빈을 등록하면 대체된다</li>
 *     <li>접근 로그 필터: iturtle.web.access-log.enabled=false 로 끄거나 accessLogFilterRegistration 이름의 빈으로 대체한다</li>
 *     <li>외부 호출 로그: iturtle.web.client-log.enabled=false 로 끈다. Spring Boot 의 RestClient.Builder 로 만든 RestClient 에만 적용된다</li>
 * </ul>
 * BasicErrorController 보다 먼저 ErrorController 를 등록하기 위해 ErrorMvcAutoConfiguration 보다 먼저 실행된다.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AccessLogProperties.class)
public class CommonWebMvcAutoConfiguration {

    /**
     * Spring Security 필터(-100) 다음에 실행되어 인증 정보를 MdcContributor 에서 사용할 수 있다.
     * Micrometer 추적 필터는 이보다 먼저 실행되므로 traceId 는 이미 MDC 에 있다.
     */
    static final int MDC_FILTER_ORDER = -99;

    /**
     * MdcFilter 다음에 실행되어 MdcContributor 가 넣은 값(user.id 등)이 접근 로그에도 포함된다.
     */
    static final int ACCESS_LOG_FILTER_ORDER = -98;

    @Bean
    @ConditionalOnMissingBean
    public MvcGlobalExceptionHandler mvcGlobalExceptionHandler() {
        return new MvcGlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(ErrorController.class)
    public MvcErrorController mvcErrorController(ObjectProvider<ErrorAttributes> errorAttributes) {
        return new MvcErrorController(errorAttributes.getIfAvailable(DefaultErrorAttributes::new));
    }

    @Bean
    @ConditionalOnMissingBean(name = "mdcFilterRegistration")
    public FilterRegistrationBean<MdcFilter> mdcFilterRegistration(ObjectProvider<MdcContributor> contributors) {
        FilterRegistrationBean<MdcFilter> registration =
                new FilterRegistrationBean<>(new MdcFilter(contributors.orderedStream().toList()));
        registration.setOrder(MDC_FILTER_ORDER);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(name = "accessLogFilterRegistration")
    @ConditionalOnProperty(prefix = "iturtle.web.access-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AccessLogFilter> accessLogFilterRegistration(AccessLogProperties properties) {
        FilterRegistrationBean<AccessLogFilter> registration =
                new FilterRegistrationBean<>(new AccessLogFilter(properties));
        registration.setOrder(ACCESS_LOG_FILTER_ORDER);
        // 예외로 끝난 요청은 에러 디스패치가 끝난 뒤 최종 상태 코드로 기록한다
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(name = "clientLogRestClientCustomizer")
    @ConditionalOnProperty(prefix = "iturtle.web.client-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RestClientCustomizer clientLogRestClientCustomizer() {
        ClientLogInterceptor interceptor = new ClientLogInterceptor();
        return builder -> builder.requestInterceptor(interceptor);
    }
}
