package com.gym.fitness.module.lead.repository;
import com.gym.fitness.module.lead.entity.LeadTrialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LeadTrialRepository extends JpaRepository<LeadTrialEntity,Long> {
    List<LeadTrialEntity> findByLeadIdOrderByIdDesc(Long leadId);
    List<LeadTrialEntity> findByVenueIdAndHostIdAndStatus(Long venueId,Long hostId,String status);
    boolean existsByLeadIdAndStatus(Long leadId,String status);
    List<LeadTrialEntity> findByLeadIdInAndStatus(Collection<Long> leadIds,String status);
}
