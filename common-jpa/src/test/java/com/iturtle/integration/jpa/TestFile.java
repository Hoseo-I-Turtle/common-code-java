package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.storage.StorageEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class TestFile extends StorageEntity {

    @Id
    @GeneratedValue
    private Long id;

    protected TestFile() {
    }

    TestFile(String storageKey) {
        super(storageKey, "a.png", "image/png", 10);
    }
}
