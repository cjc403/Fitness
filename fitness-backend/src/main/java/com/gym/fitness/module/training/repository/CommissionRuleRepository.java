package com.gym.fitness.module.training.repository;
import com.gym.fitness.module.training.entity.CommissionRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CommissionRuleRepository extends JpaRepository<CommissionRuleEntity,Long> {
    Optional<CommissionRuleEntity> findByVenueIdAndCoachId(Long venueId,Long coachId);
    List<CommissionRuleEntity> findByVenueId(Long venueId);
}
