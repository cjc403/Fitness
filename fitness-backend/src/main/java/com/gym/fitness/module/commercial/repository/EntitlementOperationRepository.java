package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.EntitlementOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntitlementOperationRepository extends JpaRepository<EntitlementOperationEntity, Long> {
    List<EntitlementOperationEntity> findByEntitlementIdOrderByOperatedAtDesc(Long entitlementId);
}
