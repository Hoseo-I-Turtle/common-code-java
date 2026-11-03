package com.iturtle.integration.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestOutboxRepository extends JpaRepository<TestOutbox, Long> {
}
