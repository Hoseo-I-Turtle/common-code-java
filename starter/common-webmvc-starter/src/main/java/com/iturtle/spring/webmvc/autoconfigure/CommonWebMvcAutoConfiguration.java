package com.iturtle.spring.webmvc.autoconfigure;

import com.iturtle.spring.webmvc.logging.AccessLogFilter;
import com.iturtle.spring.web.logging.AccessLogProperties;
import com.iturtle.spring.webmvc.exception.MvcGlobalExceptionHandler;
import com.iturtle.spring.webmvc.logging.MdcContributor;
import com.iturtle.spring.webmvc.logging.MdcFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import com.iturtle.spring.web.json.PatchOptionalJackson2Module;
import com.iturtle.spring.web.json.PatchOptionalJackson3Module;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 서블릿 웹 애플리케이션에 전역 예외 핸들러, MDC 필터, 접근 로그 필터를 등록한다.
 * <ul>
 *     <li>PATCH 본문: 보내지 않은 {@code Optional} 칸은 null ({@code PatchOptionalJackson2Module} · {@code PatchOptionalJackson3Module}).
 *     iturtle.web.json.absent-optional-as-null=false 로 끈다</li>
 *     <li>예외 핸들러: MvcGlobalExceptionHandler 타입 빈을 등록하면 대체된다</li>
 *     <li>MDC 필터: mdcFilterRegistration 이름의 빈을 등록하면 대체된다</li>
 *     <li>접근 로그 필터: iturtle.web.access-log.enabled=false 로 끄거나 accessLogFilterRegistration 이름의 빈으로 대체한다</li>
 * </ul>
 * 에러 컨트롤러와 외부 호출 로그는 Spring Boot 버전별 자동 설정(Boot3WebMvcAutoConfiguration, Boot4WebMvcAutoConfiguration)이 등록한다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(AccessLogProperties.class)
@Import({CommonWebMvcAutoConfiguration.Jackson3PatchConfiguration.class,
        CommonWebMvcAutoConfiguration.Jackson2PatchConfiguration.class})
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

    // Spring Boot 가 JacksonModule(3) · Module(2) 빈을 JSON 매퍼에 넣는다.
    // Jackson 타입은 이 안에서만 참조해 없어도 클래스 로딩이 깨지지 않는다
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "tools.jackson.databind.JacksonModule")
    @ConditionalOnProperty(prefix = "iturtle.web.json", name = "absent-optional-as-null", havingValue = "true", matchIfMissing = true)
    static class Jackson3PatchConfiguration {

        @Bean
        @ConditionalOnMissingBean
        PatchOptionalJackson3Module patchOptionalJackson3Module() {
            return new PatchOptionalJackson3Module();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.Module")
    @ConditionalOnProperty(prefix = "iturtle.web.json", name = "absent-optional-as-null", havingValue = "true", matchIfMissing = true)
    static class Jackson2PatchConfiguration {

        @Bean
        @ConditionalOnMissingBean
        PatchOptionalJackson2Module patchOptionalJackson2Module() {
            return new PatchOptionalJackson2Module();
        }
    }
}
