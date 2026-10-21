package com.iturtle.spring.security.webmvc.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iturtle.spring.security.webmvc.ApiAccessDeniedHandler;
import com.iturtle.spring.security.webmvc.ApiAuthenticationEntryPoint;
import com.iturtle.spring.security.webmvc.SecurityErrorResponseWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Security 오류를 ApiBaseResponse 로 응답하는 빈을 등록한다. 각 빈은 같은 타입의 빈을 등록하면 대체된다.
 * <p>
 * 필터 단계(인증 실패 / 권한 없음 / JWT)는 서비스가 SecurityFilterChain 에 직접 연결한다.
 * JwtAuthenticationFilter 는 빈이 아니므로 체인 설정에서 만든다.
 * <pre>
 * &#64;Bean
 * SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProviders jwtProviders,
 *                                         SecurityErrorResponseWriter writer,
 *                                         AuthenticationEntryPoint entryPoint,
 *                                         AccessDeniedHandler accessDeniedHandler) throws Exception {
 *     return http
 *             .csrf(AbstractHttpConfigurer::disable)
 *             .sessionManagement(session -&gt; session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
 *             .authorizeHttpRequests(auth -&gt; auth
 *                     .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()   // /error 응답이 401 로 바뀌지 않게
 *                     .anyRequest().authenticated())
 *             .exceptionHandling(handling -&gt; handling
 *                     .authenticationEntryPoint(entryPoint)
 *                     .accessDeniedHandler(accessDeniedHandler))
 *             .addFilterBefore(new JwtAuthenticationFilter(jwtProviders.get("access"), writer),
 *                     UsernamePasswordAuthenticationFilter.class)
 *             .build();
 * }
 * </pre>
 * 컨트롤러 단계(@PreAuthorize 거부 등)는 전역 예외 핸들러(AbstractGlobalExceptionHandler)가 처리한다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(AuthenticationEntryPoint.class)
public class CommonSecurityWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecurityErrorResponseWriter securityErrorResponseWriter(ObjectProvider<ObjectMapper> objectMapper) {
        return new SecurityErrorResponseWriter(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationEntryPoint apiAuthenticationEntryPoint(SecurityErrorResponseWriter writer) {
        return new ApiAuthenticationEntryPoint(writer);
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessDeniedHandler apiAccessDeniedHandler(SecurityErrorResponseWriter writer) {
        return new ApiAccessDeniedHandler(writer);
    }
}
