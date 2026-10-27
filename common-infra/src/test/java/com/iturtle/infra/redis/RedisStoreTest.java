package com.iturtle.infra.redis;

import com.iturtle.core.exception.CommonErrorCode;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 가 응답하지 않을 때 기본 메서드는 「없음」으로 답하고 OrThrow 메서드는 예외를 던지는지 검증한다 (실제 Redis 없이).
 */
class RedisStoreTest {

    private static final RedisConnectionFailureException DOWN = new RedisConnectionFailureException("connection refused");

    private StringRedisTemplate template;
    private ValueOperations<String, String> values;
    private RedisStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        template = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        store = new RedisStore(template);
    }

    @Test
    void lenientMethodsAnswerEmptyWhenDown() {
        when(values.get("k")).thenThrow(DOWN);
        when(values.multiGet(List.of("a", "b"))).thenThrow(DOWN);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenThrow(DOWN);
        when(template.hasKey("k")).thenThrow(DOWN);

        assertThat(store.get("k")).isEmpty();
        assertThat(store.multiGet(List.of("a", "b"))).isEqualTo(Arrays.asList(null, null));
        assertThat(store.setIfAbsent("k", "v", Duration.ofSeconds(10))).isFalse();
        assertThat(store.hasKey("k")).isFalse();
    }

    @Test
    void orThrowMethodsThrowWhenDown() {
        when(values.get("k")).thenThrow(DOWN);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenThrow(DOWN);

        when(values.get("auth:attempt:1234")).thenThrow(DOWN);
        assertThatThrownBy(() -> store.getOrThrow("auth:attempt:1234"))
                .isInstanceOf(RedisUnavailableException.class)
                .hasMessageContaining("key=auth:attempt:****")
                .hasMessageNotContaining("1234")
                .satisfies(e -> assertThat(((RedisUnavailableException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR));
        assertThatThrownBy(() -> store.setIfAbsentOrThrow("k", "v", Duration.ofSeconds(10)))
                .isInstanceOf(RedisUnavailableException.class);
    }

    @Test
    void getOrThrowSeparatesMissingFromDown() {
        when(values.get("k")).thenReturn(null);

        assertThat(store.getOrThrow("k")).isEqualTo(Optional.empty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void incrementOrThrowSendsTtlInMillis() {
        when(template.execute(any(RedisScript.class), eq(List.of("k")), eq("60000"))).thenReturn(3L);

        assertThat(store.incrementOrThrow("k", Duration.ofMinutes(1))).isEqualTo(3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void incrementOrThrowFailsWithoutResult() {
        when(template.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> store.incrementOrThrow("k", Duration.ofMinutes(1)))
                .isInstanceOf(RedisUnavailableException.class);
    }

    @Test
    void deleteManyInOneCommand() {
        store.delete(List.of("a", "b"));
        store.delete(List.of());

        verify(template).delete(List.of("a", "b"));
        verify(template, never()).delete(List.<String>of());
    }

    @Test
    void deleteManyIgnoresFailure() {
        when(template.delete(anyCollection())).thenThrow(DOWN);

        store.delete(List.of("a", "b"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void multiSetPipelinesSetWithTtl() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("a", "1");
        entries.put("b", "2");
        ArgumentCaptor<RedisCallback<Object>> callback = ArgumentCaptor.forClass(RedisCallback.class);

        store.multiSet(entries, Duration.ofMinutes(1));

        verify(template).executePipelined(callback.capture());
        StringRedisConnection connection = mock(StringRedisConnection.class);
        callback.getValue().doInRedis(connection);
        verify(connection).set("a", "1", Expiration.from(Duration.ofMinutes(1)), SetOption.upsert());
        verify(connection).set("b", "2", Expiration.from(Duration.ofMinutes(1)), SetOption.upsert());
    }

    @Test
    @SuppressWarnings("unchecked")
    void multiSetSkipsEmptyOrExpiredAndIgnoresFailure() {
        store.multiSet(Map.of(), Duration.ofMinutes(1));
        store.multiSet(Map.of("a", "1"), Duration.ZERO);
        verify(template, never()).executePipelined(any(RedisCallback.class));

        when(template.executePipelined(any(RedisCallback.class))).thenThrow(DOWN);
        store.multiSet(Map.of("a", "1"), Duration.ofMinutes(1));
    }

    @Test
    void maskKey() {
        assertThat(RedisStore.maskKey("auth:verification:attempt:1234")).isEqualTo("auth:verification:attempt:****");
        assertThat(RedisStore.maskKey("plain")).isEqualTo("****");
    }

    @Test
    void failureLoggedAsWarnOncePerInterval() {
        AtomicLong now = new AtomicLong();
        RedisStore throttled = new RedisStore(template, now::get);
        when(values.get(anyString())).thenThrow(DOWN);
        Logger logger = (Logger) LoggerFactory.getLogger("com.iturtle.error");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            throttled.get("user:profile:15");
            throttled.get("user:profile:16");
            throttled.get("user:profile:17");
            now.addAndGet(Duration.ofSeconds(10).toNanos());
            throttled.get("user:profile:18");

            List<ILoggingEvent> warns = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
            assertThat(warns).hasSize(2);
            assertThat(warns.get(0).getFormattedMessage())
                    .contains("Redis 읽기 실패", "key=user:profile:****", "RedisConnectionFailureException")
                    .doesNotContain("15");
            assertThat(warns.get(1).getFormattedMessage()).contains("지난 10초간 2건 생략");
            assertThat(warns).allSatisfy(e -> assertThat(e.getThrowableProxy()).isNull());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void expiredTtlNotWritten() {
        store.set("k", "v", Duration.ZERO);

        assertThat(store.setIfAbsent("k", "v", Duration.ofSeconds(-1))).isFalse();
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
        verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }
}
