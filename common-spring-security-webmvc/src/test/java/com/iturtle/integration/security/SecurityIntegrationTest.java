package com.iturtle.integration.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.core.exception.ErrorCode;
import com.iturtle.spring.security.jwt.JwtErrorCode;
import com.iturtle.spring.security.jwt.JwtProvider;
import com.iturtle.spring.security.jwt.JwtProviders;
import com.iturtle.spring.security.jwt.TokenBlacklist;
import com.iturtle.spring.security.webmvc.JwtAuthenticationFilter;
import com.iturtle.spring.security.webmvc.JwtBlacklistFilter;
import com.iturtle.spring.security.webmvc.SecurityErrorResponseWriter;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * README / javadoc 에 적은 SecurityFilterChain 구성 그대로 조립해 실제 요청으로 검증한다.
 */
@SpringBootTest(
        classes = SecurityIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "iturtle.security.jwt.enabled=true",
                "iturtle.security.jwt.tokens.access.secret=0123456789abcdef0123456789abcdef",
                "iturtle.security.jwt.tokens.refresh.secret=0123456789abcdef0123456789abcdef"
        })
class SecurityIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtProviders jwtProviders;

    @Autowired
    private TokenBlacklist tokenBlacklist;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void noTokenOnProtectedPathIs401C002() throws Exception {
        assertError(get("/me", null), 401, CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void validTokenAuthenticatesWithUserDetails() {
        ResponseEntity<String> response = get("/me", accessToken("user"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("user", response.getBody());
    }

    @Test
    void urlRuleDeniedIs403C003() throws Exception {
        assertError(get("/admin/ping", accessToken("user")), 403, CommonErrorCode.FORBIDDEN);
        assertEquals(200, get("/admin/ping", accessToken("admin")).getStatusCode().value());
    }

    @Test
    void invalidTokensRejected() throws Exception {
        assertError(get("/me", "abc.def.ghi"), 401, JwtErrorCode.INVALID_TOKEN);
        String refreshToken = jwtProviders.get("refresh").createToken("user", Map.of());
        assertError(get("/me", refreshToken), 401, JwtErrorCode.INVALID_TOKEN);
    }

    @Test
    void unknownUserIs401C002() throws Exception {
        assertError(get("/me", accessToken("deleted")), 401, CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void revokedTokenIs401J003() throws Exception {
        String token = accessToken("user");
        tokenBlacklist.revoke(jwtProviders.get("access").parse(token));

        assertError(get("/me", token), 401, JwtErrorCode.REVOKED_TOKEN);
    }

    @Test
    void preAuthorizeDeniedInController() throws Exception {
        // 로그인한 사용자의 권한 부족 -> 403, 로그인하지 않은 사용자 -> 401 (전역 예외 핸들러가 판단)
        assertError(get("/method/admin", accessToken("user")), 403, CommonErrorCode.FORBIDDEN);
        assertError(get("/public/method-admin", null), 401, CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    void errorDispatchNotBlockedBySecurity() throws Exception {
        // sendError -> /error 디스패치도 Security 체인을 거친다. ERROR 를 permitAll 하지 않으면 401 로 바뀐다
        assertError(get("/public/send-error", null), 409, CommonErrorCode.CONFLICT);
    }

    private String accessToken(String username) {
        return jwtProviders.get("access").createToken(username, Map.of());
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void assertError(ResponseEntity<String> response, int status, ErrorCode errorCode) throws Exception {
        assertEquals(status, response.getStatusCode().value(), response.getBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertFalse(body.get("success").asBoolean());
        assertEquals(errorCode.getCode(), body.get("error").get("code").asText());
    }

    // 테스트 클래스에 중첩된 클래스는 component scan 에서 제외되므로 직접 등록한다
    @SpringBootApplication
    @EnableMethodSecurity
    @Import(TestController.class)
    static class TestApplication {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProviders jwtProviders,
                                                SecurityErrorResponseWriter writer,
                                                UserDetailsService userDetailsService,
                                                TokenBlacklist tokenBlacklist,
                                                AuthenticationEntryPoint entryPoint,
                                                AccessDeniedHandler accessDeniedHandler) throws Exception {
            JwtProvider access = jwtProviders.get("access");
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers("/public/**").permitAll()
                            .requestMatchers("/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .exceptionHandling(handling -> handling
                            .authenticationEntryPoint(entryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .addFilterBefore(new JwtAuthenticationFilter(access, writer, userDetailsService),
                            UsernamePasswordAuthenticationFilter.class)
                    .addFilterBefore(new JwtBlacklistFilter(access, tokenBlacklist, writer), JwtAuthenticationFilter.class)
                    .build();
        }

        @Bean
        UserDetailsService userDetailsService() {
            return username -> switch (username) {
                case "user" -> User.withUsername("user").password("").roles("USER").build();
                case "admin" -> User.withUsername("admin").password("").roles("ADMIN").build();
                default -> throw new UsernameNotFoundException(username);
            };
        }

        @Bean
        TokenBlacklist tokenBlacklist() {
            Set<String> revoked = ConcurrentHashMap.newKeySet();
            return new TokenBlacklist() {
                @Override
                public void revoke(JWTClaimsSet claims) {
                    revoked.add(claims.getJWTID());
                }

                @Override
                public boolean isRevoked(JWTClaimsSet claims) {
                    return revoked.contains(claims.getJWTID());
                }
            };
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/me")
        String me(@AuthenticationPrincipal UserDetails user) {
            return user.getUsername();
        }

        @GetMapping("/admin/ping")
        String adminPing() {
            return "pong";
        }

        @GetMapping("/method/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String methodAdmin() {
            return "ok";
        }

        @GetMapping("/public/method-admin")
        @PreAuthorize("hasRole('ADMIN')")
        String publicMethodAdmin() {
            return "ok";
        }

        @GetMapping("/public/send-error")
        void sendError(HttpServletResponse response) throws IOException {
            response.sendError(409);
        }
    }
}
