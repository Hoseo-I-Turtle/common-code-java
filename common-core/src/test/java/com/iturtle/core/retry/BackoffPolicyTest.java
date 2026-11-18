package com.iturtle.core.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BackoffPolicyTest {

    private final BackoffPolicy policy =
            BackoffPolicy.exponential(Duration.ofMinutes(1), 2.0, Duration.ofMinutes(10), 5);

    @Test
    void delayGrowsUntilMax() {
        assertEquals(Duration.ofMinutes(1), policy.delayAfter(1));
        assertEquals(Duration.ofMinutes(2), policy.delayAfter(2));
        assertEquals(Duration.ofMinutes(4), policy.delayAfter(3));
        assertEquals(Duration.ofMinutes(8), policy.delayAfter(4));
        assertEquals(Duration.ofMinutes(10), policy.delayAfter(5));
        assertEquals(Duration.ofMinutes(10), policy.delayAfter(10_000));   // 넘치지 않는다
        assertThrows(IllegalArgumentException.class, () -> policy.delayAfter(0));
    }

    @Test
    void nextAttemptAtStopsWhenExhausted() {
        Instant now = Instant.parse("2026-11-18T00:00:00Z");
        assertEquals(Optional.of(now.plus(Duration.ofMinutes(4))), policy.nextAttemptAt(3, now));
        assertFalse(policy.exhausted(4));
        assertTrue(policy.exhausted(5));
        assertEquals(Optional.empty(), policy.nextAttemptAt(5, now));
    }

    @Test
    void fixed() {
        BackoffPolicy fixed = BackoffPolicy.fixed(Duration.ofSeconds(30), 3);
        assertEquals(Duration.ofSeconds(30), fixed.delayAfter(1));
        assertEquals(Duration.ofSeconds(30), fixed.delayAfter(2));
        assertTrue(fixed.exhausted(3));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> BackoffPolicy.fixed(Duration.ZERO, 3));
        assertThrows(IllegalArgumentException.class, () -> BackoffPolicy.fixed(Duration.ofSeconds(1), 0));
        assertThrows(IllegalArgumentException.class,
                () -> BackoffPolicy.exponential(Duration.ofMinutes(2), 2.0, Duration.ofMinutes(1), 3));
        assertThrows(IllegalArgumentException.class,
                () -> BackoffPolicy.exponential(Duration.ofMinutes(1), 0.5, Duration.ofMinutes(5), 3));
        assertThrows(IllegalArgumentException.class,
                () -> BackoffPolicy.exponential(Duration.ofMinutes(1), Double.NaN, Duration.ofMinutes(5), 3));
        assertThrows(NullPointerException.class, () -> BackoffPolicy.fixed(null, 3));
    }
}
