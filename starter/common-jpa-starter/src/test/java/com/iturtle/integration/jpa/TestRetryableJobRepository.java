package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.job.RetryableJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface TestRetryableJobRepository extends JpaRepository<TestRetryableJob, Long> {

    @Query("select j from TestRetryableJob j where j.status = :status and j.nextRunAt <= :now order by j.nextRunAt")
    List<TestRetryableJob> findDue(RetryableJobStatus status, Instant now);
}
