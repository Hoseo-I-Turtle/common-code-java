package com.iturtle.spring.security.jwt;

import com.iturtle.core.exception.InvalidConfigurationException;

import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 설정된 토큰 종류별 JwtProvider 묶음. 이름(access, refresh 등)으로 꺼내 사용한다.
 * 모든 토큰의 설정을 시작 시 검증하므로, 하나라도 잘못되면 애플리케이션 시작이 실패한다.
 */
public class JwtProviders {

    private final Map<String, JwtProvider> providers;

    public JwtProviders(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public JwtProviders(JwtProperties properties, Clock clock) {
        if (properties.getTokens().isEmpty()) {
            throw new InvalidConfigurationException("iturtle.security.jwt.tokens 에 토큰을 하나 이상 설정해야 한다");
        }
        Map<String, JwtProvider> providers = new LinkedHashMap<>();
        properties.getTokens().forEach((name, settings) ->
                providers.put(name, new JwtProvider(name, settings, properties.getClockSkew(), clock)));
        this.providers = Collections.unmodifiableMap(providers);
    }

    /**
     * 설정되지 않은 이름이면 IllegalArgumentException.
     */
    public JwtProvider get(String name) {
        JwtProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "iturtle.security.jwt.tokens." + name + " 가 설정되지 않았다 (설정된 토큰: " + providers.keySet() + ")");
        }
        return provider;
    }

    public Set<String> names() {
        return providers.keySet();
    }
}
