package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.job.RetryableJobEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class TestRetryableJob extends RetryableJobEntity {

    @Id
    @GeneratedValue
    private Long id;

    protected TestRetryableJob() {
    }

    TestRetryableJob(Instant runAt) {
        super(runAt);
    }

    Long getId() {
        return id;
    }
}
