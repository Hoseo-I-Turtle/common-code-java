package com.iturtle.integration.jpa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 업로드 기록 상위 클래스(StorageEntity, PolymorphicStorageEntity)의 매핑과 생성 기록을 검증한다.
 */
@SpringBootTest(classes = StorageEntityIntegrationTest.TestApplication.class)
class StorageEntityIntegrationTest {

    @Autowired
    private TestFileRepository fileRepository;

    @Autowired
    private TestAttachmentRepository attachmentRepository;

    @Autowired
    private TestNamedAttachmentRepository namedAttachmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        fileRepository.deleteAll();
        attachmentRepository.deleteAll();
        namedAttachmentRepository.deleteAll();
    }

    @Test
    void createdAtAndCreatedByFilled() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user-1", null, List.of()));

        TestFile file = fileRepository.saveAndFlush(new TestFile("files/a.png"));

        assertNotNull(file.getCreatedAt());
        assertEquals("user-1", file.getCreatedBy());
        assertEquals("files/a.png", file.getStorageKey());
        assertEquals(10, file.getSizeBytes());
    }

    @Test
    void storageKeyUnique() {
        fileRepository.saveAndFlush(new TestFile("files/dup.png"));

        assertThrows(DataIntegrityViolationException.class,
                () -> fileRepository.saveAndFlush(new TestFile("files/dup.png")));
    }

    @Test
    void enumTargetTypeStoredAsName() {
        attachmentRepository.saveAndFlush(new TestAttachment("posts/a.png", TestTarget.POST, "1"));
        attachmentRepository.saveAndFlush(new TestAttachment("comments/a.png", TestTarget.COMMENT, "1"));

        List<TestAttachment> found = attachmentRepository.findByTargetTypeAndTargetId(TestTarget.POST, "1");

        assertEquals(1, found.size());
        assertEquals(TestTarget.POST, found.get(0).getTargetType());
        assertNotNull(found.get(0).getCreatedAt());
        assertEquals("POST", jdbcTemplate.queryForObject(
                "select target_type from test_attachment where storage_key = 'posts/a.png'", String.class));
    }

    @Test
    void stringTargetType() {
        namedAttachmentRepository.saveAndFlush(new TestNamedAttachment("posts/b.png", "post", "2"));

        List<TestNamedAttachment> found = namedAttachmentRepository.findByTargetTypeAndTargetId("post", "2");

        assertEquals(1, found.size());
        assertEquals("post", found.get(0).getTargetType());
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
