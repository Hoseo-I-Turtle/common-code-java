package com.iturtle.spring.security.autoconfigure;

import com.iturtle.spring.security.jwt.JwtProperties;
import com.iturtle.spring.security.jwt.JwtProviders;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * iturtle.security.jwt.enabled=true 일 때 JwtProviders 를 등록한다. JwtProviders 타입 빈을 등록하면 대체된다.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "iturtle.security.jwt", name = "enabled", havingValue = "true")
    public JwtProviders jwtProviders(JwtProperties properties) {
        return new JwtProviders(properties);
    }
}
