package com.gym.fitness.module.finance.repository;
import com.gym.fitness.module.finance.entity.EntitlementTermPeriodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EntitlementTermPeriodRepository extends JpaRepository<EntitlementTermPeriodEntity,Long> {
    List<EntitlementTermPeriodEntity> findByEntitlementIdOrderByIdAsc(Long id);
    List<EntitlementTermPeriodEntity> findByEntitlementIdInOrderByEntitlementIdAscIdAsc(List<Long> ids);
}
