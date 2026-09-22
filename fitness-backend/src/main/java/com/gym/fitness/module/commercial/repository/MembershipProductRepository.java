package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.MembershipProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipProductRepository extends JpaRepository<MembershipProductEntity, Long> {
    List<MembershipProductEntity> findAllByOrderByIdDesc();
    List<MembershipProductEntity> findByVenueIdOrderByIdDesc(Long venueId);
    List<MembershipProductEntity> findByVenueIdAndStatusOrderByIdDesc(Long venueId, String status);
    List<MembershipProductEntity> findByStatusOrderByIdDesc(String status);
    boolean existsByVenueIdAndNameIgnoreCase(Long venueId, String name);
}
