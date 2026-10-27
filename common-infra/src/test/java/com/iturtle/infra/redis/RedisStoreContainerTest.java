package com.iturtle.infra.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis 에서 RedisStore 를 검증한다 (Lua 스크립트, 파이프라인). Docker 가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisStoreContainerTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.12-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    private static StringRedisTemplate template;

    private final RedisStore store = new RedisStore(template);

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        template = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void flush() {
        template.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void incrementOrThrowSetsTtlOnFirstIncrement() {
        assertThat(store.incrementOrThrow("attempt:1", Duration.ofMinutes(1))).isEqualTo(1);
        assertThat(store.incrementOrThrow("attempt:1", Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(store.incrementOrThrow("attempt:1", Duration.ofMinutes(1))).isEqualTo(3);

        Long ttl = template.getExpire("attempt:1", TimeUnit.MILLISECONDS);
        assertThat(ttl).isBetween(1L, 60_000L);
    }

    @Test
    void multiSetWritesAllWithTtl() {
        store.multiSet(Map.of("cache:a", "1", "cache:b", "2"), Duration.ofMinutes(1));

        assertThat(store.multiGet(List.of("cache:a", "cache:b", "cache:missing"))).isEqualTo(Arrays.asList("1", "2", null));
        assertThat(template.getExpire("cache:a", TimeUnit.MILLISECONDS)).isBetween(1L, 60_000L);
        assertThat(template.getExpire("cache:b", TimeUnit.MILLISECONDS)).isBetween(1L, 60_000L);
    }

    @Test
    void setIfAbsentAndDelete() {
        assertThat(store.setIfAbsentOrThrow("lock:1", "a", Duration.ofMinutes(1))).isTrue();
        assertThat(store.setIfAbsentOrThrow("lock:1", "b", Duration.ofMinutes(1))).isFalse();
        assertThat(store.getOrThrow("lock:1")).contains("a");

        store.set("lock:2", "c", Duration.ofMinutes(1));
        store.delete(List.of("lock:1", "lock:2"));

        assertThat(store.hasKey("lock:1")).isFalse();
        assertThat(store.getOrThrow("lock:2")).isEmpty();
    }
}
