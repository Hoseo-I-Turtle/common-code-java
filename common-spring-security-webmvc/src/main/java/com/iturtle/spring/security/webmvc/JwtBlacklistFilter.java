package com.iturtle.spring.security.webmvc;

import com.iturtle.spring.security.jwt.InvalidJwtException;
import com.iturtle.spring.security.jwt.JwtErrorCode;
import com.iturtle.spring.security.jwt.JwtProvider;
import com.iturtle.spring.security.jwt.TokenBlacklist;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authorization: Bearer 토큰이 TokenBlacklist 에서 무효화되었으면 401 J003 으로 응답한다.
 * <ul>
 *     <li>토큰 없음 / 토큰이 잘못됨: 판단하지 않고 통과한다. 응답은 JwtAuthenticationFilter 가 한다</li>
 *     <li>무효화된 토큰: 즉시 401 J003</li>
 * </ul>
 * 다른 필터에 기대지 않도록 토큰을 직접 검증한 뒤 확인한다. 위조된 jti 로 블랙리스트를 조회하지 않기 위해서다.
 * <p>
 * JwtAuthenticationFilter 와 같은 JwtProvider 로 만들고, 그 앞에 둔다. 무효화된 토큰으로 UserDetailsService 를
 * 호출하지 않게 하기 위해서다. 빈으로 등록하지 않는다(Spring Boot 가 서블릿 필터로도 등록한다).
 * <pre>
 * JwtProvider access = jwtProviders.get("access");
 * http.addFilterBefore(new JwtAuthenticationFilter(access, writer, userDetailsService),
 *                 UsernamePasswordAuthenticationFilter.class)
 *         .addFilterBefore(new JwtBlacklistFilter(access, tokenBlacklist, writer), JwtAuthenticationFilter.class)
 * </pre>
 */
public class JwtBlacklistFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final TokenBlacklist tokenBlacklist;
    private final SecurityErrorResponseWriter writer;

    public JwtBlacklistFilter(JwtProvider jwtProvider, TokenBlacklist tokenBlacklist, SecurityErrorResponseWriter writer) {
        this.jwtProvider = jwtProvider;
        this.tokenBlacklist = tokenBlacklist;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = BearerTokens.resolve(request);
        if (token != null) {
            JWTClaimsSet claims = parseOrNull(token);
            if (claims != null && tokenBlacklist.isRevoked(claims)) {
                writer.write(response, JwtErrorCode.REVOKED_TOKEN,
                        new InvalidJwtException(JwtErrorCode.REVOKED_TOKEN, "무효화된 토큰 (jti " + claims.getJWTID() + ")"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private JWTClaimsSet parseOrNull(String token) {
        try {
            return jwtProvider.parse(token);
        } catch (InvalidJwtException e) {
            return null;
        }
    }
}
