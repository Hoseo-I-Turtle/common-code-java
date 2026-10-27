package com.iturtle.infra.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Redis 를 직접 쓰는 것들의 공용 창구 (캐시, 블랙리스트, 레이트 리밋, 분산 락 등). 키와 TTL 은 부르는 쪽이 정하도록 문자열 그대로 다룬다.
 * <p>
 * 대부분의 메서드는 실패를 삼키고 「없음」으로 답한다. 이름이 OrThrow 로 끝나는 것만 실패를 RedisUnavailableException 으로 던진다.
 * 동시성을 지키는 값은 실패를 「없음」으로 넘기면 제한이 조용히 풀리므로, 부르는 쪽이 장애를 알고 다른 길(DB 잠금 등)로 가야 한다.
 * <p>
 * 실패 로그
 * <ul>
 *     <li>삼킨 실패는 처리된 장애이므로 WARN 이다. OrThrow 는 로그를 남기지 않고, 던진 예외를 전역 예외 핸들러가 남긴다</li>
 *     <li>Redis 가 내려가면 요청마다 실패하므로, 같은 작업의 실패는 10초에 한 번만 남기고 그 사이 생략한 건수를 함께 남긴다</li>
 *     <li>키는 마지막 ':' 뒤(식별자 자리)를 가리고 접두사만 남긴다. 예외 메시지도 같다</li>
 *     <li>스택 트레이스는 DEBUG 에서만 남긴다</li>
 * </ul>
 */
@Slf4j(topic = "com.iturtle.error")
public class RedisStore {

    // INCR 과 PEXPIRE 를 따로 보내면 그 사이에 끊겼을 때 TTL 없는 키가 영원히 남는다. 한 번에 묶는다.
    private static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private static final Duration LOG_INTERVAL = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;

    private final LongSupplier nanoTime;

    private final Map<String, LogThrottle> logThrottles = new ConcurrentHashMap<>();

    public RedisStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, System::nanoTime);
    }

    /**
     * 테스트에서 로그 간격을 검증할 때 쓴다.
     */
    RedisStore(StringRedisTemplate redisTemplate, LongSupplier nanoTime) {
        this.redisTemplate = redisTemplate;
        this.nanoTime = nanoTime;
    }

    /**
     * 하나 올리고 올린 값을 준다. 키를 처음 만들 때만 TTL 을 건다. 올릴 때마다 걸면 계속 시도하는 동안 키가 사라지지 않는다.
     */
    public long incrementOrThrow(String key, Duration ttl) {
        try {
            Long count = redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), String.valueOf(ttl.toMillis()));
            if (count == null) {
                throw new RedisUnavailableException("Redis 가 증가 결과를 주지 않았다. key=" + maskKey(key), null);
            }
            return count;
        } catch (DataAccessException e) {
            throw unavailable("증가", key, e);
        }
    }

    /**
     * 키가 없을 때만 쓴다. 이미 있으면 false 다.
     */
    public boolean setIfAbsentOrThrow(String key, String value, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
        } catch (DataAccessException e) {
            throw unavailable("조건부 쓰기", key, e);
        }
    }

    public void setOrThrow(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (DataAccessException e) {
            throw unavailable("쓰기", key, e);
        }
    }

    /**
     * 값이 없는 것과 Redis 가 응답하지 않는 것을 가른다. 없으면 빈 값, 응답하지 않으면 예외다.
     */
    public Optional<String> getOrThrow(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (DataAccessException e) {
            throw unavailable("읽기", key, e);
        }
    }

    /**
     * TTL 이 0 이하이면 쓰지 않는다 (이미 만료된 값).
     */
    public void set(String key, String value, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (DataAccessException e) {
            warn("쓰기", maskKey(key), e);
        }
    }

    /**
     * 여러 키를 같은 TTL 로 쓴다. MSET 은 TTL 을 걸 수 없어 SET EX 를 파이프라인으로 한 번에 보낸다.
     * 원자적이지 않으므로 도중에 끊기면 일부만 쓰일 수 있다 (캐시처럼 없어도 되는 값에 쓴다). TTL 이 0 이하이면 쓰지 않는다.
     */
    public void multiSet(Map<String, String> values, Duration ttl) {
        if (values.isEmpty() || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        Expiration expiration = Expiration.from(ttl);
        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                StringRedisConnection strings = (StringRedisConnection) connection;
                values.forEach((key, value) -> strings.set(key, value, expiration, SetOption.upsert()));
                return null;
            });
        } catch (DataAccessException e) {
            warn("다중 쓰기", maskKeys(values.keySet()), e);
        }
    }

    /**
     * 키가 없을 때만 쓴다. 이미 있거나 Redis 가 응답하지 않으면 false 다.
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
        } catch (DataAccessException e) {
            warn("조건부 쓰기", maskKey(key), e);
            return false;
        }
    }

    /**
     * Redis 가 응답하지 않으면 값이 없는 것으로 본다. 그 뒤 판단은 부르는 쪽 몫이다.
     */
    public Optional<String> get(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (DataAccessException e) {
            warn("읽기", maskKey(key), e);
            return Optional.empty();
        }
    }

    /**
     * 여러 키를 한 번에 읽는다. 없는 키 자리에는 null 이 들어오고 순서는 넘긴 대로다. Redis 가 응답하지 않으면 전부 null 로 본다.
     */
    public List<String> multiGet(List<String> keys) {
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            return values == null ? toNulls(keys) : values;
        } catch (DataAccessException e) {
            warn("다중 읽기", maskKeys(keys), e);
            return toNulls(keys);
        }
    }

    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (DataAccessException e) {
            warn("조회", maskKey(key), e);
            return false;
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            warn("삭제", maskKey(key), e);
        }
    }

    /**
     * 여러 키를 DEL 명령 하나로 지운다. 없는 키는 무시한다.
     */
    public void delete(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (DataAccessException e) {
            warn("다중 삭제", maskKeys(keys), e);
        }
    }

    /**
     * 마지막 ':' 뒤(식별자 자리)를 가린다. auth:verification:attempt:1234 → auth:verification:attempt:****
     */
    static String maskKey(String key) {
        int last = key.lastIndexOf(':');
        return last < 0 ? "****" : key.substring(0, last + 1) + "****";
    }

    private static List<String> maskKeys(Collection<String> keys) {
        return keys.stream().map(RedisStore::maskKey).distinct().toList();
    }

    private RedisUnavailableException unavailable(String operation, String key, DataAccessException e) {
        return new RedisUnavailableException(
                "Redis " + operation + " 실패. key=" + maskKey(key) + ", cause=" + causeOf(e), e);
    }

    private void warn(String operation, Object keys, DataAccessException e) {
        long suppressed = logThrottles.computeIfAbsent(operation, ignored -> new LogThrottle())
                .tryAcquire(nanoTime.getAsLong(), LOG_INTERVAL.toNanos());
        if (suppressed == 0) {
            log.warn("Redis {} 실패. key={}, cause={}", operation, keys, causeOf(e));
        } else if (suppressed > 0) {
            log.warn("Redis {} 실패. key={}, cause={} (지난 {}초간 {}건 생략)",
                    operation, keys, causeOf(e), LOG_INTERVAL.toSeconds(), suppressed);
        }
        log.debug("Redis {} 실패 상세. key={}", operation, keys, e);
    }

    private List<String> toNulls(List<String> keys) {
        return Collections.nCopies(keys.size(), null);
    }

    /**
     * 스택 대신 가장 안쪽 원인 한 줄만 남긴다.
     */
    private String causeOf(DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    /**
     * 작업 하나의 실패 로그를 interval 에 한 번만 허용한다.
     */
    static final class LogThrottle {

        private boolean logged;

        private long nextAllowedAt;

        private long suppressed;

        /**
         * 남겨도 되면 그 전까지 생략한 건수(0 이상), 아직 간격 안이면 -1.
         */
        synchronized long tryAcquire(long now, long interval) {
            if (logged && now - nextAllowedAt < 0) {
                suppressed++;
                return -1;
            }
            long count = suppressed;
            logged = true;
            nextAllowedAt = now + interval;
            suppressed = 0;
            return count;
        }
    }
}
