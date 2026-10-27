package com.iturtle.integration.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TestRefreshTokenRepository extends JpaRepository<TestRefreshToken, Long> {

    Optional<TestRefreshToken> findByJti(UUID jti);
}
