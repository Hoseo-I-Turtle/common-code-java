package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class TestLog extends CreatedAtEntity {

    @Id
    @GeneratedValue
    private Long id;
}
