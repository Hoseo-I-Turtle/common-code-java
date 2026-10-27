package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.storage.PolymorphicStorageEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class TestNamedAttachment extends PolymorphicStorageEntity<String> {

    @Id
    @GeneratedValue
    private Long id;

    protected TestNamedAttachment() {
    }

    TestNamedAttachment(String storageKey, String targetType, String targetId) {
        super(storageKey, "a.png", "image/png", 10, targetType, targetId);
    }
}
