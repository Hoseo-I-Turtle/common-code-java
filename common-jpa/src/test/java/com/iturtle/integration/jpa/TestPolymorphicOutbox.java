package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.messaging.PolymorphicOutboxEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class TestPolymorphicOutbox extends PolymorphicOutboxEntity<TestEvent, TestTarget, UUID> {

    @Id
    @GeneratedValue
    private Long id;

    protected TestPolymorphicOutbox() {
    }

    TestPolymorphicOutbox(UUID eventId, TestTarget aggregateType, String aggregateId, TestEvent eventType,
                          String payload) {
        super(eventId, aggregateType, aggregateId, eventType, payload);
    }

    Long getId() {
        return id;
    }
}
