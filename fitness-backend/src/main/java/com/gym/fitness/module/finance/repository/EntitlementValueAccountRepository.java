package com.gym.fitness.module.finance.repository;
import com.gym.fitness.module.finance.entity.EntitlementValueAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface EntitlementValueAccountRepository extends JpaRepository<EntitlementValueAccountEntity,Long> {
    Optional<EntitlementValueAccountEntity> findByEntitlementId(Long id);
    List<EntitlementValueAccountEntity> findByMemberId(Long id);
    List<EntitlementValueAccountEntity> findByVenueId(Long id);
}
