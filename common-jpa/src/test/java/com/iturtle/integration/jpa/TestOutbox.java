package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.messaging.OutboxEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class TestOutbox extends OutboxEntity<String, String> {

    @Id
    @GeneratedValue
    private Long id;

    protected TestOutbox() {
    }

    TestOutbox(String eventId, String eventType, String payload) {
        super(eventId, eventType, payload);
    }

    Long getId() {
        return id;
    }
}
