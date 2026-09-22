package com.gym.fitness.module.finance.repository;
import com.gym.fitness.module.finance.entity.EntitlementFundingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EntitlementFundingRepository extends JpaRepository<EntitlementFundingEntity,Long> {
    List<EntitlementFundingEntity> findByEntitlementIdOrderByOrderIdAsc(Long id);
    List<EntitlementFundingEntity> findByEntitlementIdIn(List<Long> ids);
    List<EntitlementFundingEntity> findByVenueId(Long venueId);
    Optional<EntitlementFundingEntity> findByOrderId(Long id);
}
