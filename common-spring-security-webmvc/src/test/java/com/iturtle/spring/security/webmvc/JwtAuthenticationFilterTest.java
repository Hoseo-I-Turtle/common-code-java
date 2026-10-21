package com.iturtle.spring.security.webmvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtProvider, new SecurityErrorResponseWriter(objectMapper));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenAuthenticates() throws Exception {
        String token = jwtProvider.createToken("user-1", Map.of("roles", List.of("ROLE_USER", "ROLE_ADMIN")));
        AtomicReference<Authentication> seen = new AtomicReference<>();

        filter.doFilter(request("Bearer " + token), new MockHttpServletResponse(),
                (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));

        assertEquals("user-1", seen.get().getPrincipal());
        assertEquals(List.of("ROLE_USER", "ROLE_ADMIN"),
                seen.get().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void spaceSeparatedAuthoritiesSupported() throws Exception {
        String token = jwtProvider.createToken("user-1", Map.of("roles", "ROLE_USER ROLE_ADMIN"));
        AtomicReference<Authentication> seen = new AtomicReference<>();

        filter.doFilter(request("Bearer " + token), new MockHttpServletResponse(),
                (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));

        assertEquals(2, seen.get().getAuthorities().size());
    }

    @Test
    void customAuthoritiesClaim() throws Exception {
        JwtAuthenticationFilter scopeFilter =
                new JwtAuthenticationFilter(jwtProvider, "scope", new SecurityErrorResponseWriter(objectMapper));
        String token = jwtProvider.createToken("service-a", Map.of("scope", "orders.read orders.write"));
        AtomicReference<Authentication> seen = new AtomicReference<>();

        scopeFilter.doFilter(request("Bearer " + token), new MockHttpServletResponse(),
                (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));

        assertEquals(List.of("orders.read", "orders.write"),
                seen.get().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
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

    @Test
    void refreshTokenRejected() throws Exception {
        String refreshToken = new JwtProvider("refresh", settings, SKEW, Clock.systemUTC()).createToken("user-1", Map.of());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("Bearer " + refreshToken), response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertEquals(JwtErrorCode.INVALID_TOKEN.getCode(), errorCode(response));
    }

    private static JwtProperties.Token settings() {
        JwtProperties.Token settings = new JwtProperties.Token();
        settings.setSecret("0123456789abcdef0123456789abcdef");
        return settings;
    }
}
