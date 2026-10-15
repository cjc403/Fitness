package com.gym.fitness.module.agent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
public interface AgentActionRepository extends JpaRepository<AgentActionEntity,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from AgentActionEntity a where a.id=:id")
 Optional<AgentActionEntity> lock(@Param("id") String id);
 List<AgentActionEntity> findTop30ByUserIdAndSessionHashOrderByCreatedAtDesc(Long userId,String hash);
 long countByUserIdAndSessionHashAndStatusAndExpiresAtAfter(Long userId,String hash,String status,LocalDateTime now);
}
