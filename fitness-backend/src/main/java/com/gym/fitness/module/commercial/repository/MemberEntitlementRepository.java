package com.gym.fitness.module.commercial.repository;

import com.gym.fitness.module.commercial.entity.MemberEntitlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MemberEntitlementRepository extends JpaRepository<MemberEntitlementEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from MemberEntitlementEntity e where e.id=:id")
    java.util.Optional<MemberEntitlementEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<MemberEntitlementEntity> findAllByOrderByIdDesc();
    List<MemberEntitlementEntity> findByVenueIdOrderByIdDesc(Long venueId);
    List<MemberEntitlementEntity> findByMemberIdOrderByIdDesc(Long memberId);
    List<MemberEntitlementEntity> findByMemberIdAndStatusOrderByValidUntilAsc(Long memberId, String status);
    long countByStatus(String status);
    long countByVenueIdAndStatus(Long venueId, String status);
    long countByStatusAndValidUntilBetween(String status, LocalDate from, LocalDate to);
    long countByVenueIdAndStatusAndValidUntilBetween(Long venueId, String status, LocalDate from, LocalDate to);
    long countByProductId(Long productId);
}
