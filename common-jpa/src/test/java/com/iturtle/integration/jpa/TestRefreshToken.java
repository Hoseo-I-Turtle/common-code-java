package com.iturtle.integration.jpa;

import com.iturtle.jpa.entity.auth.JwtRefreshTokenEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Entity
public class TestRefreshToken extends JwtRefreshTokenEntity<Long, TestRevokeReason> {

    @Id
    @GeneratedValue
    private Long id;

    protected TestRefreshToken() {
    }

    TestRefreshToken(Long subject, UUID familyId, UUID jti, UUID previousJti, String rawToken, Instant expiresAt) {
        super(subject, familyId, jti, previousJti, rawToken, expiresAt);
    }

    Long getId() {
        return id;
    }
}
