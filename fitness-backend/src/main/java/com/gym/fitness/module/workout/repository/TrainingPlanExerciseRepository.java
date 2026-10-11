package com.gym.fitness.module.workout.repository;
import com.gym.fitness.module.workout.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;
public interface TrainingPlanExerciseRepository extends JpaRepository<TrainingPlanExerciseEntity,Long> {
 List<TrainingPlanExerciseEntity> findByPlanIdOrderByPositionAsc(Long id);
 long countByPlanId(Long id);
 void deleteByPlanId(Long id);
}
