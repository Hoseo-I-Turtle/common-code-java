package com.iturtle.core.retry;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * 실패한 작업을 언제 다시 시도할지 정한다. 시도할 때마다 간격이 multiplier 배씩 늘고 maxDelay 를 넘지 않는다.
 * <p>
 * attempts 는 <b>지금까지 시도한 횟수</b>다 (방금 실패한 시도 포함). attempts 가 maxAttempts 이상이면 더 시도하지 않는다.
 * <pre>
 * BackoffPolicy policy = BackoffPolicy.exponential(Duration.ofMinutes(1), 2.0, Duration.ofHours(1), 6);
 * // 1번째 실패 → 1분 뒤, 2번째 → 2분 뒤, 3번째 → 4분 뒤 ... 6번째 실패 → 포기
 * Optional&lt;Instant&gt; next = policy.nextAttemptAt(attempts, Instant.now());
 * </pre>
 * 외부 서비스 동기화(Vault · Jenkins · GitHub 등) 작업과 outbox 릴레이에 쓴다 ({@code RetryableJobEntity}, {@code OutboxEntity}).
 *
 * @param initialDelay 첫 실패 뒤 기다릴 시간 (0 보다 커야 함)
 * @param multiplier   실패할 때마다 곱할 값 (1 이상. 1 이면 고정 간격)
 * @param maxDelay     간격의 상한 (initialDelay 이상)
 * @param maxAttempts  최대 시도 횟수 (1 이상)
 */
public record BackoffPolicy(Duration initialDelay, double multiplier, Duration maxDelay, int maxAttempts) {

    public BackoffPolicy {
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (initialDelay.isNegative() || initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay 는 0 보다 커야 한다");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay 는 initialDelay 이상이어야 한다");
        }
        if (!(multiplier >= 1.0) || Double.isInfinite(multiplier)) {
            throw new IllegalArgumentException("multiplier 는 1 이상이어야 한다");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 는 1 이상이어야 한다");
        }
    }

    public static BackoffPolicy exponential(Duration initialDelay, double multiplier, Duration maxDelay, int maxAttempts) {
        return new BackoffPolicy(initialDelay, multiplier, maxDelay, maxAttempts);
    }

    public static BackoffPolicy fixed(Duration delay, int maxAttempts) {
        return new BackoffPolicy(delay, 1.0, delay, maxAttempts);
    }

    /**
     * attempts 번 시도한 뒤 더 시도하지 않는지.
     */
    public boolean exhausted(int attempts) {
        return attempts >= maxAttempts;
    }

    /**
     * attempts 번째 실패 뒤 기다릴 시간. initialDelay × multiplier^(attempts-1), 상한 maxDelay.
     */
    public Duration delayAfter(int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts 는 1 이상이어야 한다");
        }
        double millis = initialDelay.toMillis() * Math.pow(multiplier, attempts - 1);
        if (Double.isInfinite(millis) || millis >= maxDelay.toMillis()) {
            return maxDelay;
        }
        return Duration.ofMillis((long) millis);
    }

    /**
     * 다음 시도 시각. 더 시도하지 않으면 비어 있다.
     */
    public Optional<Instant> nextAttemptAt(int attempts, Instant now) {
        if (exhausted(attempts)) {
            return Optional.empty();
        }
        return Optional.of(now.plus(delayAfter(attempts)));
    }
}
