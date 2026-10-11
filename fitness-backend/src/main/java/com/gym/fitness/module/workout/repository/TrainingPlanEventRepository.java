package com.gym.fitness.module.workout.repository;
import com.gym.fitness.module.workout.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;
public interface TrainingPlanEventRepository extends JpaRepository<TrainingPlanEventEntity,Long> {
 Optional<TrainingPlanEventEntity> findByMemberIdAndActorIdAndIdempotencyKey(Long member,Long actor,String key);
 List<TrainingPlanEventEntity> findTop100ByPlanIdOrderByIdDesc(Long id);
 long countByPlanId(Long id);
}
