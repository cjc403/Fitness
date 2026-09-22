package com.gym.fitness.module.training.repository;
import com.gym.fitness.module.training.entity.PrivateLessonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.LocalDateTime;
public interface PrivateLessonRepository extends JpaRepository<PrivateLessonEntity,Long> {
    Optional<PrivateLessonEntity> findByBookingId(Long bookingId);
    List<PrivateLessonEntity> findAllByOrderByReservedAtDesc();
    List<PrivateLessonEntity> findByVenueIdOrderByReservedAtDesc(Long venueId);
    List<PrivateLessonEntity> findByMemberIdOrderByReservedAtDesc(Long memberId);
    List<PrivateLessonEntity> findByCoachIdOrderByReservedAtDesc(Long coachId);
    List<PrivateLessonEntity> findByCoachIdAndVenueIdAndStatusAndSettlementIdIsNullAndConfirmedAtGreaterThanEqualAndConfirmedAtLessThan(Long coachId,Long venueId,String status,LocalDateTime from,LocalDateTime to);
    List<PrivateLessonEntity> findBySettlementId(Long id);
    boolean existsByEntitlementIdAndStatus(Long id,String status);
    long countByScheduleId(Long scheduleId);
}
