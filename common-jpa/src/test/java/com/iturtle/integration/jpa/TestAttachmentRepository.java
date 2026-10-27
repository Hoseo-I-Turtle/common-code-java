package com.iturtle.integration.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestAttachmentRepository extends JpaRepository<TestAttachment, Long> {

    List<TestAttachment> findByTargetTypeAndTargetId(TestTarget targetType, String targetId);
}
