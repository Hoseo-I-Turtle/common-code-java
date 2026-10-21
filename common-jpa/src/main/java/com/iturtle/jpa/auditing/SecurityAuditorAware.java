package com.iturtle.jpa.auditing;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 로그인 사용자의 Authentication.getName() 을 생성자 / 수정자로 사용한다. 로그인하지 않았거나 익명 사용자면 비워 둔다.
 */
public class SecurityAuditorAware implements AuditorAware<String> {

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || trustResolver.isAnonymous(authentication)) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName());
    }

}
