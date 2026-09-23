package com.gym.fitness.module.review.repository;

import com.gym.fitness.module.review.entity.CourseReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CourseReviewRepository extends JpaRepository<CourseReviewEntity, Long> {
    boolean existsByBookingId(Long bookingId);
    List<CourseReviewEntity> findByMemberIdOrderByIdDesc(Long memberId);
    List<CourseReviewEntity> findByCoachIdOrderByIdDesc(Long coachId);
    List<CourseReviewEntity> findByVenueIdOrderByIdDesc(Long venueId);
    List<CourseReviewEntity> findAllByOrderByIdDesc();
    List<CourseReviewEntity> findByBookingIdIn(Collection<Long> bookingIds);
}
