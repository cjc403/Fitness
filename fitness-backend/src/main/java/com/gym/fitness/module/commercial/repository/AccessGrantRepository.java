package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.AccessGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AccessGrantRepository extends JpaRepository<AccessGrantEntity, Long> {

    /** 扫码时加行锁读取，保证同一个令牌不会被两次扫码同时消费。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from AccessGrantEntity g where g.token = :token")
    Optional<AccessGrantEntity> findByTokenForUpdate(@Param("token") String token);

    /** 清理过期令牌用（保留最近的记录便于排查）。 */
    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
