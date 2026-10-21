package com.iturtle.spring.security.webmvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iturtle.spring.security.jwt.JwtErrorCode;
import com.iturtle.spring.security.jwt.JwtProperties;
import com.iturtle.spring.security.jwt.JwtProvider;
import com.iturtle.spring.security.jwt.TokenBlacklist;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtBlacklistFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtProvider jwtProvider = new JwtProvider("access", settings(), Duration.ofSeconds(30), Clock.systemUTC());
    private final InMemoryBlacklist blacklist = new InMemoryBlacklist();
    private final JwtBlacklistFilter filter =
            new JwtBlacklistFilter(jwtProvider, blacklist, new SecurityErrorResponseWriter(objectMapper));

    @Test
    void revokedTokenRespondsJ003() throws Exception {
        String token = jwtProvider.createToken("user-1", Map.of());
        blacklist.revoke(jwtProvider.parse(token));
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("Bearer " + token), response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals(JwtErrorCode.REVOKED_TOKEN.getCode(),
                objectMapper.readTree(response.getContentAsString()).get("error").get("code").asText());
    }

    @Test
    void otherTokenOfSameUserPasses() throws Exception {
        blacklist.revoke(jwtProvider.parse(jwtProvider.createToken("user-1", Map.of())));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("Bearer " + jwtProvider.createToken("user-1", Map.of())), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void noTokenOrInvalidTokenPassesToNextFilter() throws Exception {
        MockFilterChain noToken = new MockFilterChain();
        MockFilterChain invalidToken = new MockFilterChain();

        filter.doFilter(request(null), new MockHttpServletResponse(), noToken);
        filter.doFilter(request("Bearer abc.def.ghi"), new MockHttpServletResponse(), invalidToken);

        assertNotNull(noToken.getRequest());
        assertNotNull(invalidToken.getRequest());
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private static JwtProperties.Token settings() {
        JwtProperties.Token settings = new JwtProperties.Token();
        settings.setSecret("0123456789abcdef0123456789abcdef");
        return settings;
    }

    private static class InMemoryBlacklist implements TokenBlacklist {

        private final Set<String> revoked = new HashSet<>();

        @Override
        public void revoke(JWTClaimsSet claims) {
            revoked.add(claims.getJWTID());
        }

        @Override
        public boolean isRevoked(JWTClaimsSet claims) {
            return revoked.contains(claims.getJWTID());
        }
    }
}
