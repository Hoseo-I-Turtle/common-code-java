package com.iturtle.infra.autoconfigure;

import com.iturtle.infra.redis.RedisStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * iturtle.infra.redis.enabled=true 이고 StringRedisTemplate 빈이 있을 때 RedisStore 를 등록한다.
 * <p>
 * 연결과 StringRedisTemplate 은 Spring Boot(spring.data.redis.*)가 만든다. 이 설정은 연결을 만들지 않는다.
 * 그 빈을 보도록 Boot 의 RedisAutoConfiguration 다음에 실행한다. 서비스가 RedisStore 빈을 직접 등록하면 그것을 쓴다.
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "iturtle.infra.redis", name = "enabled", havingValue = "true")
public class RedisStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StringRedisTemplate.class)
    RedisStore redisStore(StringRedisTemplate redisTemplate) {
        return new RedisStore(redisTemplate);
    }
}
