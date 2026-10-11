package com.gym.fitness.module.auth.repository;

import com.gym.fitness.module.auth.entity.RevokedAuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedAuthSessionRepository extends JpaRepository<RevokedAuthSessionEntity, String> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from RevokedAuthSessionEntity s where s.expiresAt < :before")
    int deleteExpiredBefore(@org.springframework.data.repository.query.Param("before") java.time.Instant before);
}
