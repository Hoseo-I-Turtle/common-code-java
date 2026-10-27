package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.job.JobEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class TestJob extends JobEntity<TestJobStatus> {

    @Id
    @GeneratedValue
    private Long id;

    protected TestJob() {
    }

    TestJob(TestJobStatus status) {
        super(status);
    }

    Long getId() {
        return id;
    }
}
