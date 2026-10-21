package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.SoftDeleteTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class TestCode extends SoftDeleteTimeEntity {

    @Id
    @GeneratedValue
    private Long id;

    Long getId() {
        return id;
    }
}
