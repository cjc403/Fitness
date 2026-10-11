package com.gym.fitness.module.cashshift.repository;
import com.gym.fitness.module.cashshift.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CashShiftEventRepository extends JpaRepository<CashShiftEventEntity,Long> {
Optional<CashShiftEventEntity> findByActorIdAndRequestKey(Long actor,String key); List<CashShiftEventEntity> findByShiftIdOrderByIdAsc(Long id);
}
