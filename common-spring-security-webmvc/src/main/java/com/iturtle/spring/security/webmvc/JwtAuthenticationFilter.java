package com.iturtle.spring.security.webmvc;

import com.iturtle.spring.security.jwt.InvalidJwtException;
import com.iturtle.spring.security.jwt.JwtProvider;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Authorization: Bearer 토큰을 생성자로 받은 JwtProvider(토큰 한 종류)로 검증해 SecurityContext 에 인증 정보를 넣는다.
 * 다른 종류의 토큰(refresh 등)은 typ 이 달라 거부된다.
 * <ul>
 *     <li>토큰 없음: 인증 없이 통과한다. 인증이 필요한 경로면 이후 AuthenticationEntryPoint 가 401 로 응답한다</li>
 *     <li>토큰이 잘못됨: 경로와 관계없이 즉시 401 로 응답한다 (만료 J001, 그 외 J002)</li>
 *     <li>토큰이 유효함: principal 은 sub, 권한은 권한 클레임(기본 roles) 값, details 는 전체 클레임(JWTClaimsSet)</li>
 * </ul>
 * 권한 값은 접두사 없이 그대로 사용하므로 hasRole("ADMIN") 을 쓰려면 토큰에 ROLE_ADMIN 을 담는다.
 * <p>
 * Security 필터 체인 안에서만 동작해야 하므로 빈으로 등록하지 않고(Spring Boot 가 서블릿 필터로도 등록한다)
 * SecurityFilterChain 설정에서 직접 만들어 추가한다. URL 별로 토큰이 다르면 체인을 나누고 체인마다 만든다.
 * <pre>
 * http.addFilterBefore(new JwtAuthenticationFilter(jwtProviders.get("access"), writer),
 *         UsernamePasswordAuthenticationFilter.class)
 * </pre>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String DEFAULT_AUTHORITIES_CLAIM = "roles";

    private final JwtProvider jwtProvider;
    private final String authoritiesClaim;
    private final SecurityErrorResponseWriter writer;
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    /**
     * 권한을 roles 클레임에서 읽는다.
     */
    public JwtAuthenticationFilter(JwtProvider jwtProvider, SecurityErrorResponseWriter writer) {
        this(jwtProvider, DEFAULT_AUTHORITIES_CLAIM, writer);
    }

    /**
     * @param jwtProvider      인증에 사용할 토큰(보통 access)의 JwtProvider
     * @param authoritiesClaim 권한 목록을 담은 클레임 이름
     */
    public JwtAuthenticationFilter(JwtProvider jwtProvider, String authoritiesClaim, SecurityErrorResponseWriter writer) {
        this.jwtProvider = jwtProvider;
        this.authoritiesClaim = authoritiesClaim;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                authenticate(jwtProvider.parse(token));
            } catch (InvalidJwtException e) {
                contextHolderStrategy.clearContext();
                writer.write(response, e.getErrorCode(), e);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void authenticate(JWTClaimsSet claims) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(claims.getSubject(), null, authorities(claims));
        authentication.setDetails(claims);

        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
    }

    /**
     * 권한 클레임은 배열(["ROLE_USER"]) 또는 공백 / 쉼표로 구분한 문자열("ROLE_USER ROLE_ADMIN")을 허용한다.
     */
    private List<GrantedAuthority> authorities(JWTClaimsSet claims) {
        Object value = claims.getClaim(authoritiesClaim);
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                    .toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Arrays.stream(text.split("[\\s,]+"))
                    .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                    .toList();
        }
        return List.of();
    }
}
