package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.storage.PolymorphicStorageEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class TestAttachment extends PolymorphicStorageEntity<TestTarget> {

    @Id
    @GeneratedValue
    private Long id;

    protected TestAttachment() {
    }

    TestAttachment(String storageKey, TestTarget targetType, String targetId) {
        super(storageKey, "a.png", "image/png", 10, targetType, targetId);
    }
}
