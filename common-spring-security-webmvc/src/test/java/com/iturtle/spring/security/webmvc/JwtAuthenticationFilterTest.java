package com.iturtle.spring.security.webmvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.security.jwt.JwtErrorCode;
import com.iturtle.spring.security.jwt.JwtProperties;
import com.iturtle.spring.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private static final Duration SKEW = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtProperties.Token settings = settings();
    private final JwtProvider jwtProvider = new JwtProvider("access", settings, SKEW, Clock.systemUTC());

    // user-1: 정상, user-2: 비활성, 그 외: 없음
    private final UserDetailsService userDetailsService = username -> switch (username) {
        case "user-1" -> User.withUsername("user-1").password("").authorities("ROLE_USER", "ROLE_ADMIN").build();
        case "user-2" -> User.withUsername("user-2").password("").authorities("ROLE_USER").disabled(true).build();
        default -> throw new UsernameNotFoundException(username);
    };

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtProvider, new SecurityErrorResponseWriter(objectMapper), userDetailsService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenAuthenticatesWithUserDetails() throws Exception {
        // 토큰의 다른 클레임은 인증에 쓰이지 않는다
        String token = jwtProvider.createToken("user-1", Map.of("roles", List.of("ROLE_IGNORED")));
        AtomicReference<Authentication> seen = new AtomicReference<>();

        filter.doFilter(request("Bearer " + token), new MockHttpServletResponse(),
                (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));

        assertEquals("user-1", ((UserDetails) seen.get().getPrincipal()).getUsername());
        assertEquals(List.of("ROLE_ADMIN", "ROLE_USER"),
                seen.get().getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList());
    }

    @Test
    void unknownUserRespondsC002() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("Bearer " + jwtProvider.createToken("deleted-user", Map.of())), response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals(CommonErrorCode.UNAUTHORIZED.getCode(), errorCode(response));
    }

    @Test
    void disabledUserRespondsC002() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("Bearer " + jwtProvider.createToken("user-2", Map.of())), response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertEquals(CommonErrorCode.UNAUTHORIZED.getCode(), errorCode(response));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void noTokenPassesWithoutAuthentication() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(null), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidTokenRespondsJ002AndStops() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("Bearer abc.def.ghi"), response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals(JwtErrorCode.INVALID_TOKEN.getCode(), errorCode(response));
    }

    @Test
    void expiredTokenRespondsJ001() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        String token = new JwtProvider("access", settings, SKEW, Clock.fixed(past, ZoneOffset.UTC))
                .createToken("user-1", Map.of(), Duration.ofMinutes(5));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("Bearer " + token), response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertEquals(JwtErrorCode.EXPIRED_TOKEN.getCode(), errorCode(response));
    }

    @Test
    void refreshTokenRejected() throws Exception {
        String refreshToken = new JwtProvider("refresh", settings, SKEW, Clock.systemUTC()).createToken("user-1", Map.of());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("Bearer " + refreshToken), response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertEquals(JwtErrorCode.INVALID_TOKEN.getCode(), errorCode(response));
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private String errorCode(MockHttpServletResponse response) throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        return body.get("error").get("code").asText();
    }

    private static JwtProperties.Token settings() {
        JwtProperties.Token settings = new JwtProperties.Token();
        settings.setSecret("0123456789abcdef0123456789abcdef");
        return settings;
    }
}
