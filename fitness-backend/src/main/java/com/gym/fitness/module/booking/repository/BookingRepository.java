package com.gym.fitness.module.booking.repository;

import com.gym.fitness.module.booking.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    /** 该会员是否在该教练的某条排课上有过预约（教练学员判定用）。 */
    @org.springframework.data.jpa.repository.Query("select count(b) > 0 from BookingEntity b "
            + "where b.memberId = :memberId and b.schedule.coachId = :coachId")
    boolean existsByMemberIdAndScheduleCoachId(@org.springframework.data.repository.query.Param("memberId") Long memberId,
                                              @org.springframework.data.repository.query.Param("coachId") Long coachId);
    @org.springframework.data.jpa.repository.Query("select b.schedule.venueId from BookingEntity b where b.id=:id")
    Optional<Long> findVenueId(@org.springframework.data.repository.query.Param("id") Long id);
    @org.springframework.data.jpa.repository.Query("select b.scheduleId from BookingEntity b where b.id=:id")
    Optional<Long> findScheduleId(@org.springframework.data.repository.query.Param("id") Long id);
    List<BookingEntity> findAllByOrderByBookedAtDesc();
    List<BookingEntity> findBySchedule_VenueIdOrderByBookedAtDesc(Long venueId);
    boolean existsByScheduleIdAndMemberIdAndStatusNot(Long scheduleId, Long memberId, String status);
    Optional<BookingEntity> findByScheduleIdAndMemberId(Long scheduleId, Long memberId);
    Optional<BookingEntity> findByIdAndMemberId(Long id, Long memberId);
    List<BookingEntity> findByMemberIdOrderByBookedAtDesc(Long memberId);
    List<BookingEntity> findByScheduleIdInOrderByBookedAtAsc(List<Long> scheduleIds);
    long countByStatus(String status);
    long countBySchedule_VenueId(Long venueId);
    long countBySchedule_VenueIdAndStatus(Long venueId, String status);
    long countByScheduleIdAndStatusNot(Long scheduleId, String status);
    long countByScheduleIdAndStatus(Long scheduleId, String status);
    long countByScheduleId(Long scheduleId);
    Optional<BookingEntity> findFirstByScheduleIdAndStatusOrderByWaitlistPositionAscBookedAtAsc(Long scheduleId, String status);
    /** 已结束却仍未核销的预约数（全局，供启动自检；不涉及登录上下文）。 */
    long countByStatusInAndSchedule_ScheduledEndTimeBefore(List<String> statuses, java.time.LocalDateTime before);
}
