package com.gym.fitness.module.finance.repository;
import com.gym.fitness.module.finance.entity.EntitlementValueEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
public interface EntitlementValueEventRepository extends JpaRepository<EntitlementValueEventEntity,Long> {
    Optional<EntitlementValueEventEntity> findByBusinessKey(String key);
    List<EntitlementValueEventEntity> findByEntitlementIdOrderByIdAsc(Long id);
    List<EntitlementValueEventEntity> findByEntitlementIdInOrderByEntitlementIdAscIdAsc(List<Long> ids);
    List<EntitlementValueEventEntity> findByVenueIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByIdAsc(Long venueId, LocalDateTime from, LocalDateTime until);
    List<EntitlementValueEventEntity> findByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByIdAsc(LocalDateTime from, LocalDateTime until);
}
