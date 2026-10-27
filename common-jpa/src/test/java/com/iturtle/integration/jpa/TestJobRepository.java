package com.iturtle.integration.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestJobRepository extends JpaRepository<TestJob, Long> {
}
