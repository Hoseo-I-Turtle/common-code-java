package com.iturtle.infra.redis;

import com.iturtle.infra.autoconfigure.RedisStoreAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * RedisStore 자동 설정의 등록 조건을 검증한다.
 */
class RedisStoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisStoreAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void notRegisteredByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RedisStore.class));
    }

    @Test
    void registeredWhenEnabled() {
        runner.withPropertyValues("iturtle.infra.redis.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(RedisStore.class));
    }

    @Test
    void notRegisteredWithoutTemplate() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisStoreAutoConfiguration.class))
                .withPropertyValues("iturtle.infra.redis.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(RedisStore.class));
    }

    @Test
    void notRegisteredWithoutSpringDataRedis() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .withPropertyValues("iturtle.infra.redis.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(RedisStore.class));
    }

    @Test
    void serviceBeanUsed() {
        RedisStore custom = new RedisStore(mock(StringRedisTemplate.class));
        runner.withBean(RedisStore.class, () -> custom)
                .withPropertyValues("iturtle.infra.redis.enabled=true")
                .run(context -> assertThat(context).getBean(RedisStore.class).isSameAs(custom));
    }
}
