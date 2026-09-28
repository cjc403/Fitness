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

    long countByRoomId(Long roomId);

    List<CourseScheduleEntity> findByRoomIdAndScheduledStartTimeBetweenOrderByScheduledStartTimeAsc(
            Long roomId, LocalDateTime from, LocalDateTime to);

    /** 同一教室在同一时段的其它排课（冲突检测；excludeId 传 0 表示新建）。 */
    @Query("select s from CourseScheduleEntity s where s.roomId = :roomId and s.id <> :excludeId "
            + "and s.status <> 'CANCELLED' and s.scheduledStartTime < :end and s.scheduledEndTime > :start "
            + "order by s.scheduledStartTime asc")
    List<CourseScheduleEntity> findRoomOverlaps(@Param("roomId") Long roomId,
                                                @Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("excludeId") Long excludeId);

    /**
     * 同一教练在同一时间段内的其它排课（用于冲突检测）。
     * 重叠判定：已有排课.start &lt; 新排课.end 且 已有排课.end &gt; 新排课.start。
     * {@code excludeId} 用于"编辑排课时排除自身"，新建时传 0 即可（没有 id 为 0 的排课）。
     */
    @Query("select s from CourseScheduleEntity s where s.coachId = :coachId and s.id <> :excludeId "
            + "and s.status <> 'CANCELLED' and s.scheduledStartTime < :end and s.scheduledEndTime > :start "
            + "order by s.scheduledStartTime asc")
    List<CourseScheduleEntity> findCoachOverlaps(@Param("coachId") Long coachId,
                                                 @Param("start") LocalDateTime start,
                                                 @Param("end") LocalDateTime end,
                                                 @Param("excludeId") Long excludeId);
}
