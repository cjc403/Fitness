package com.gym.fitness.module.lead.repository;
import com.gym.fitness.module.lead.entity.LeadEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LeadEventRepository extends JpaRepository<LeadEventEntity,Long> {
    Optional<LeadEventEntity> findByActorIdAndRequestKey(Long actorId,String requestKey);
    List<LeadEventEntity> findByLeadIdOrderByIdDesc(Long leadId);
}
