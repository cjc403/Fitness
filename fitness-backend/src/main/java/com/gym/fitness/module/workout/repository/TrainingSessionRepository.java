package com.gym.fitness.module.workout.repository;
import com.gym.fitness.module.workout.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;
public interface TrainingSessionRepository extends JpaRepository<TrainingSessionEntity,Long> {
 Page<TrainingSessionEntity> findByPlanIdOrderByPerformedOnDescIdDesc(Long id,Pageable page);
 long countByPlanIdAndStatus(Long id,String status);
 Optional<TrainingSessionEntity> findFirstByPlanIdAndStatusOrderByPerformedOnDescIdDesc(Long id,String status);
 boolean existsByActiveBookingKey(Long id);
}
