package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.AccessRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessRecordRepository extends JpaRepository<AccessRecordEntity, Long> {
    List<AccessRecordEntity> findTop100ByOrderByScannedAtDesc();
    List<AccessRecordEntity> findTop100ByVenueIdOrderByScannedAtDesc(Long venueId);
    long countByResultAndScannedAtBetween(String result, LocalDateTime from, LocalDateTime to);
    long countByVenueIdAndResultAndScannedAtBetween(Long venueId, String result, LocalDateTime from, LocalDateTime to);
}
