package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.messaging.InboxStatus;
import com.iturtle.jpa.entity.messaging.OutboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox / Inbox 상위 클래스의 매핑과 진행 상황 전이를 검증한다.
 */
@SpringBootTest(classes = MessagingEntityIntegrationTest.TestApplication.class)
class MessagingEntityIntegrationTest {

    @Autowired
    private TestOutboxRepository outboxRepository;

    @Autowired
    private TestPolymorphicOutboxRepository polymorphicOutboxRepository;

    @Autowired
    private TestInboxRepository inboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        polymorphicOutboxRepository.deleteAll();
        inboxRepository.deleteAll();
    }

    @Test
    void outboxCreatedPending() {
        TestOutbox outbox = outboxRepository.saveAndFlush(new TestOutbox("e-1", "OrderCreated", "{}"));

        assertEquals("e-1", outbox.getEventId());
        assertNotNull(outbox.getCreatedAt());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
        assertEquals(0, outbox.getAttempts());
        assertNull(outbox.getPublishedAt());
        assertEquals("PENDING", jdbcTemplate.queryForObject("select status from test_outbox", String.class));
    }

    @Test
    void outboxEventIdGivenByService() {
        TestOutbox outbox = outboxRepository.saveAndFlush(new TestOutbox("order-15-created", "OrderCreated", "{}"));

        assertEquals("order-15-created", outboxRepository.findById(outbox.getId()).orElseThrow().getEventId());
        assertThrows(DataIntegrityViolationException.class, () -> outboxRepository.saveAndFlush(
                new TestOutbox("order-15-created", "OrderCreated", "{}")));
    }

    @Test
    void outboxFailedThenPublished() {
        Long id = outboxRepository.saveAndFlush(new TestOutbox("e-2", "OrderCreated", "{}")).getId();

        TestOutbox outbox = outboxRepository.findById(id).orElseThrow();
        outbox.markFailed();
        outboxRepository.saveAndFlush(outbox);
        TestOutbox failed = outboxRepository.findById(id).orElseThrow();
        assertEquals(OutboxStatus.FAILED, failed.getStatus());
        assertEquals(1, failed.getAttempts());

        failed.markPublished();
        outboxRepository.saveAndFlush(failed);
        TestOutbox published = outboxRepository.findById(id).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, published.getStatus());
        assertEquals(2, published.getAttempts());
        assertNotNull(published.getPublishedAt());
    }

    @Test
    void longPayloadStored() {
        String payload = "x".repeat(10_000);

        Long id = outboxRepository.saveAndFlush(new TestOutbox("e-3", "OrderCreated", payload)).getId();

        assertEquals(payload, outboxRepository.findById(id).orElseThrow().getPayload());
    }

    @Test
    void polymorphicOutboxEnumsStoredAsName() {
        TestPolymorphicOutbox outbox = polymorphicOutboxRepository.saveAndFlush(
                new TestPolymorphicOutbox("e-4", TestTarget.POST, "1", TestEvent.CREATED, "{}"));

        TestPolymorphicOutbox found = polymorphicOutboxRepository.findById(outbox.getId()).orElseThrow();
        assertEquals(TestTarget.POST, found.getAggregateType());
        assertEquals("1", found.getAggregateId());
        assertEquals(TestEvent.CREATED, found.getEventType());
        assertEquals("POST", jdbcTemplate.queryForObject(
                "select aggregate_type from test_polymorphic_outbox", String.class));
        assertEquals("CREATED", jdbcTemplate.queryForObject(
                "select event_type from test_polymorphic_outbox", String.class));
    }

    @Test
    void inboxProcessed() {
        Long id = inboxRepository.saveAndFlush(new TestInbox("m-1", TestEvent.CREATED, "{}")).getId();
        TestInbox received = inboxRepository.findById(id).orElseThrow();
        assertEquals(InboxStatus.RECEIVED, received.getStatus());
        assertEquals(TestEvent.CREATED, received.getEventType());
        assertEquals("CREATED", jdbcTemplate.queryForObject("select event_type from test_inbox", String.class));
        assertNotNull(received.getCreatedAt());

        received.markProcessed();
        inboxRepository.saveAndFlush(received);
        TestInbox processed = inboxRepository.findById(id).orElseThrow();
        assertEquals(InboxStatus.PROCESSED, processed.getStatus());
        assertEquals(1, processed.getAttempts());
        assertNotNull(processed.getProcessedAt());
    }

    @Test
    void duplicateMessageIdRejected() {
        inboxRepository.saveAndFlush(new TestInbox("m-dup", TestEvent.CREATED, "{}"));

        assertThrows(DataIntegrityViolationException.class,
                () -> inboxRepository.saveAndFlush(new TestInbox("m-dup", TestEvent.CREATED, "{}")));
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
