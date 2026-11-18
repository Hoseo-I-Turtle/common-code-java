package com.iturtle.integration.jpa;

import com.iturtle.core.retry.BackoffPolicy;
import com.iturtle.jpa.entity.job.RetryableJobEntity;
import com.iturtle.jpa.entity.job.RetryableJobStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 재시도 작업 상위 클래스(RetryableJobEntity)의 상태 전이 · 다음 시도 시각 · 컬럼 매핑을 검증한다.
 */
@SpringBootTest(classes = RetryableJobEntityIntegrationTest.TestApplication.class)
class RetryableJobEntityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final BackoffPolicy POLICY =
            BackoffPolicy.exponential(Duration.ofMinutes(1), 2.0, Duration.ofHours(1), 3);

    @Autowired
    private TestRetryableJobRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void createdPendingAndDue() {
        TestRetryableJob job = repository.saveAndFlush(new TestRetryableJob(NOW));

        assertEquals(RetryableJobStatus.PENDING, job.getStatus());
        assertEquals(0, job.getAttempts());
        assertTrue(job.isDue(NOW));
        assertFalse(job.isDue(NOW.minusSeconds(1)));
        assertEquals("PENDING", jdbcTemplate.queryForObject("select status from test_retryable_job", String.class));
        assertEquals(1, repository.findDue(RetryableJobStatus.PENDING, NOW).size());
        assertEquals(0, repository.findDue(RetryableJobStatus.PENDING, NOW.minusSeconds(1)).size());
    }

    @Test
    void failRetriesThenGivesUp() {
        Long id = repository.saveAndFlush(new TestRetryableJob(NOW)).getId();

        TestRetryableJob job = repository.findById(id).orElseThrow();
        job.start();
        job.fail("Jenkins 가 응답하지 않았어요", POLICY, NOW);
        repository.saveAndFlush(job);
        TestRetryableJob first = repository.findById(id).orElseThrow();
        assertEquals(RetryableJobStatus.PENDING, first.getStatus());
        assertEquals(1, first.getAttempts());
        assertEquals(NOW.plus(Duration.ofMinutes(1)), first.getNextRunAt());
        assertEquals("Jenkins 가 응답하지 않았어요", first.getLastError());
        assertNotNull(first.getStartedAt());
        assertNull(first.getFinishedAt());

        first.start();
        first.fail("again", POLICY, NOW);
        assertEquals(NOW.plus(Duration.ofMinutes(2)), first.getNextRunAt());

        first.start();
        first.fail("last", POLICY, NOW);   // 3번째 실패 = 다 씀
        repository.saveAndFlush(first);
        TestRetryableJob failed = repository.findById(id).orElseThrow();
        assertEquals(RetryableJobStatus.FAILED, failed.getStatus());
        assertEquals(3, failed.getAttempts());
        assertNull(failed.getNextRunAt());
        assertNotNull(failed.getFinishedAt());
        assertFalse(failed.isDue(NOW.plus(Duration.ofDays(1))));

        // 사람이 "다시 시도" 를 누르면 재시도 규칙을 처음부터 적용한다
        failed.retryNow(NOW);
        repository.saveAndFlush(failed);
        TestRetryableJob retried = repository.findById(id).orElseThrow();
        assertEquals(RetryableJobStatus.PENDING, retried.getStatus());
        assertEquals(0, retried.getAttempts());
        assertNull(retried.getFinishedAt());
        assertEquals("last", retried.getLastError());
        assertTrue(retried.isDue(NOW));
    }

    @Test
    void succeedClearsSchedule() {
        Long id = repository.saveAndFlush(new TestRetryableJob(NOW)).getId();
        TestRetryableJob job = repository.findById(id).orElseThrow();
        job.start();
        job.fail("temporary", POLICY, NOW);
        job.start();
        job.succeed();
        repository.saveAndFlush(job);

        TestRetryableJob done = repository.findById(id).orElseThrow();
        assertEquals(RetryableJobStatus.SUCCEEDED, done.getStatus());
        assertEquals(2, done.getAttempts());
        assertNull(done.getNextRunAt());
        assertNull(done.getLastError());
        assertNotNull(done.getFinishedAt());
        assertThrows(IllegalStateException.class, () -> done.retryNow(NOW));
    }

    @Test
    void requeueStuckRunningJob() {
        TestRetryableJob job = repository.saveAndFlush(new TestRetryableJob(NOW));
        job.start();
        job.requeue(NOW.plusSeconds(600));

        assertEquals(RetryableJobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getAttempts());
        assertTrue(job.isDue(NOW.plusSeconds(600)));
    }

    @Test
    void illegalTransitionsRejected() {
        TestRetryableJob job = new TestRetryableJob(NOW);
        assertThrows(IllegalStateException.class, job::succeed);
        assertThrows(IllegalStateException.class, () -> job.fail("x", POLICY, NOW));
        assertThrows(IllegalStateException.class, () -> job.requeue(NOW));
        job.start();
        assertThrows(IllegalStateException.class, job::start);
        assertThrows(IllegalStateException.class, () -> job.retryNow(NOW));
    }

    @Test
    void longErrorTruncatedAndColumnsMapped() {
        TestRetryableJob job = repository.saveAndFlush(new TestRetryableJob(NOW));
        job.start();
        job.fail("x".repeat(5_000), POLICY, NOW);
        repository.saveAndFlush(job);

        assertEquals(RetryableJobEntity.LAST_ERROR_MAX_LENGTH,
                repository.findById(job.getId()).orElseThrow().getLastError().length());
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'TEST_RETRYABLE_JOB'",
                String.class);
        assertTrue(columns.containsAll(
                        List.of("ATTEMPTS", "NEXT_RUN_AT", "LAST_ERROR", "STATUS", "STARTED_AT", "FINISHED_AT", "CREATED_AT")),
                columns.toString());
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
