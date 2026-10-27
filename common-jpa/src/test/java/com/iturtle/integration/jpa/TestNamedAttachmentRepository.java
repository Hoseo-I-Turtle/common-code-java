package com.iturtle.integration.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestNamedAttachmentRepository extends JpaRepository<TestNamedAttachment, Long> {

    List<TestNamedAttachment> findByTargetTypeAndTargetId(String targetType, String targetId);
}
