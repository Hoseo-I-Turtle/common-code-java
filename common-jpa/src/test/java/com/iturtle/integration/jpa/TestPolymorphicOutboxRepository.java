package com.iturtle.integration.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestPolymorphicOutboxRepository extends JpaRepository<TestPolymorphicOutbox, Long> {
}
