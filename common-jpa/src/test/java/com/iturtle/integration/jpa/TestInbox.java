package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.messaging.InboxEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class TestInbox extends InboxEntity<TestEvent> {

    @Id
    @GeneratedValue
    private Long id;

    protected TestInbox() {
    }

    TestInbox(String messageId, TestEvent eventType, String payload) {
        super(messageId, eventType, payload);
    }

    Long getId() {
        return id;
    }
}
