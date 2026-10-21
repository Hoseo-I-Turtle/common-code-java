package com.iturtle.spring.security.webmvc;

import com.iturtle.core.exception.CommonErrorCode;
import com.iturtle.spring.security.jwt.InvalidJwtException;
import com.iturtle.spring.security.jwt.JwtProvider;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authorization: Bearer 토큰을 생성자로 받은 JwtProvider(토큰 한 종류)로 검증하고, sub 로 UserDetailsService 에서 사용자를
 * 조회해 SecurityContext 에 인증 정보를 넣는다. 토큰에서는 sub 만 사용하므로 그 외 클레임 구성은 서비스가 자유롭게 정한다.
 * <ul>
 *     <li>토큰 없음: 인증 없이 통과한다. 인증이 필요한 경로면 이후 AuthenticationEntryPoint 가 401 로 응답한다</li>
 *     <li>토큰이 잘못됨: 경로와 관계없이 즉시 401 로 응답한다 (만료 J001, 그 외 J002). 다른 종류의 토큰(refresh 등)도 거부된다</li>
 *     <li>사용자가 없거나 비활성 / 잠김 / 만료 계정: 즉시 401 C002</li>
 *     <li>정상: principal 은 UserDetails, 권한은 UserDetails.getAuthorities(), details 는 전체 클레임(JWTClaimsSet)</li>
 * </ul>
 * 요청마다 UserDetailsService 를 호출하므로 DB 를 조회한다면 캐시를 고려한다. 대신 권한 변경 / 계정 정지가 바로 반영된다.
 * <p>
 * Security 필터 체인 안에서만 동작해야 하므로 빈으로 등록하지 않고(Spring Boot 가 서블릿 필터로도 등록한다)
 * SecurityFilterChain 설정에서 직접 만들어 추가한다. URL 별로 토큰이 다르면 체인을 나누고 체인마다 만든다.
 * <pre>
 * http.addFilterBefore(new JwtAuthenticationFilter(jwtProviders.get("access"), writer, userDetailsService),
 *         UsernamePasswordAuthenticationFilter.class)
 * </pre>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final SecurityErrorResponseWriter writer;
    private final UserDetailsService userDetailsService;
    private final UserDetailsChecker userDetailsChecker = new AccountStatusUserDetailsChecker();
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    /**
     * @param jwtProvider        인증에 사용할 토큰(보통 access)의 JwtProvider
     * @param userDetailsService sub 로 사용자를 조회한다
     */
    public JwtAuthenticationFilter(JwtProvider jwtProvider, SecurityErrorResponseWriter writer,
                                   UserDetailsService userDetailsService) {
        this.jwtProvider = jwtProvider;
        this.writer = writer;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                JWTClaimsSet claims = jwtProvider.parse(token);
                UserDetails user = userDetailsService.loadUserByUsername(claims.getSubject());
                userDetailsChecker.check(user);
                authenticate(user, claims);
            } catch (InvalidJwtException e) {
                contextHolderStrategy.clearContext();
                writer.write(response, e.getErrorCode(), e);
                return;
            } catch (UsernameNotFoundException | AccountStatusException e) {
                // 토큰은 유효하지만 탈퇴 / 정지된 사용자
                contextHolderStrategy.clearContext();
                writer.write(response, CommonErrorCode.UNAUTHORIZED, e);
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

    private void authenticate(UserDetails user, JWTClaimsSet claims) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        authentication.setDetails(claims);

        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
    }
}
