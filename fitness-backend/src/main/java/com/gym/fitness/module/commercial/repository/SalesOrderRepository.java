package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.SalesOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrderEntity, Long> {
    /** 某人开的单（顾问业绩）。 */
    List<SalesOrderEntity> findByOperatorIdOrderByIdDesc(Long operatorId);
    List<SalesOrderEntity> findAllByOrderByCreatedAtDesc();
    List<SalesOrderEntity> findByVenueIdOrderByCreatedAtDesc(Long venueId);
    List<SalesOrderEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId);
    List<SalesOrderEntity> findByVenueIdAndPaidAtBetween(Long venueId, LocalDateTime from, LocalDateTime to);
    List<SalesOrderEntity> findByPaidAtBetween(LocalDateTime from, LocalDateTime to);
    long countByProductId(Long productId);
    Optional<SalesOrderEntity> findByOperatorIdAndIdempotencyKey(Long operatorId, String idempotencyKey);
}
