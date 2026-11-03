package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.SoftDeleteAuditEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class TestOrder extends SoftDeleteAuditEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    protected TestOrder() {
    }

    TestOrder(String name) {
        this.name = name;
    }

    Long getId() {
        return id;
    }

    void rename(String name) {
        this.name = name;
    }
}
