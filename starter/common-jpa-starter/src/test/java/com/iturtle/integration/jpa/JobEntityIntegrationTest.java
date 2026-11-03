package com.iturtle.integration.jpa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 작업 상위 클래스(JobEntity)의 진행 상황과 시작 / 종료 시각 기록을 검증한다.
 */
@SpringBootTest(classes = JobEntityIntegrationTest.TestApplication.class)
class JobEntityIntegrationTest {

    @Autowired
    private TestJobRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    void receivedNotStarted() {
        TestJob job = repository.saveAndFlush(new TestJob(TestJobStatus.RECEIVED));

        assertNotNull(job.getCreatedAt());
        assertEquals(TestJobStatus.RECEIVED, job.getStatus());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());
        assertEquals("RECEIVED", jdbcTemplate.queryForObject("select status from test_job", String.class));
    }

    @Test
    void runningThenFinished() throws Exception {
        Long id = repository.saveAndFlush(new TestJob(TestJobStatus.RECEIVED)).getId();

        TestJob job = repository.findById(id).orElseThrow();
        job.markRunning(TestJobStatus.DOWNLOADING);
        repository.saveAndFlush(job);
        TestJob downloading = repository.findById(id).orElseThrow();
        Instant startedAt = downloading.getStartedAt();
        assertEquals(TestJobStatus.DOWNLOADING, downloading.getStatus());
        assertNotNull(startedAt);

        // 다음 단계로 진행해도 처음 시작 시각을 유지한다
        Thread.sleep(10);
        downloading.markRunning(TestJobStatus.RESIZING);
        repository.saveAndFlush(downloading);
        TestJob resizing = repository.findById(id).orElseThrow();
        assertEquals(TestJobStatus.RESIZING, resizing.getStatus());
        assertEquals(startedAt, resizing.getStartedAt());
        assertNull(resizing.getFinishedAt());

        resizing.markFinished(TestJobStatus.DONE);
        repository.saveAndFlush(resizing);
        TestJob done = repository.findById(id).orElseThrow();
        assertEquals(TestJobStatus.DONE, done.getStatus());
        assertEquals(startedAt, done.getStartedAt());
        assertNotNull(done.getFinishedAt());
        assertEquals("DONE", jdbcTemplate.queryForObject("select status from test_job", String.class));
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
