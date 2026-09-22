package com.gym.fitness.module.course.repository;

import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

public interface CourseScheduleRepository extends JpaRepository<CourseScheduleEntity, Long> {
    List<CourseScheduleEntity> findByScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(LocalDateTime from, LocalDateTime to);
    List<CourseScheduleEntity> findByVenueIdAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
            Long venueId, LocalDateTime from, LocalDateTime to);
    long countByScheduledStartTimeBetween(LocalDateTime from, LocalDateTime to);
    long countByVenueIdAndScheduledStartTimeBetween(Long venueId, LocalDateTime from, LocalDateTime to);
    long countByCourseId(Long courseId);
    List<CourseScheduleEntity> findByVenueIdAndStatusAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
            Long venueId, String status, LocalDateTime from, LocalDateTime to);
    List<CourseScheduleEntity> findByCoachIdAndVenueIdAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
            Long coachId, Long venueId, LocalDateTime from, LocalDateTime to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CourseScheduleEntity s where s.id = :id")
    Optional<CourseScheduleEntity> findByIdForUpdate(@Param("id") Long id);
}
