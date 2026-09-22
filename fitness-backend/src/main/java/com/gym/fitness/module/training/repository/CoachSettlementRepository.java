package com.gym.fitness.module.training.repository;
import com.gym.fitness.module.training.entity.CoachSettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CoachSettlementRepository extends JpaRepository<CoachSettlementEntity,Long> {
    Optional<CoachSettlementEntity> findByVenueIdAndCoachIdAndMonth(Long venueId,Long coachId,String month);
    List<CoachSettlementEntity> findAllByOrderByCreatedAtDesc();
    List<CoachSettlementEntity> findByVenueIdOrderByCreatedAtDesc(Long venueId);
    List<CoachSettlementEntity> findByCoachIdOrderByCreatedAtDesc(Long coachId);
}
