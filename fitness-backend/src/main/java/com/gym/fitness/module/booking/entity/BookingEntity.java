package com.gym.fitness.module.booking.entity;

import com.gym.fitness.module.course.entity.CourseScheduleEntity;
import com.gym.fitness.module.member.entity.MemberEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking", uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_id", "member_id"}))
@Getter @Setter @NoArgsConstructor
public class BookingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "schedule_id", insertable = false, updatable = false)
    private CourseScheduleEntity schedule;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", insertable = false, updatable = false)
    private MemberEntity member;
    @Column(length = 20)
    private String status;
    @Column(name = "booked_at")
    private LocalDateTime bookedAt;
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;
    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;
    @Column(name = "waitlist_position")
    private Integer waitlistPosition;
    @Column(name = "leave_requested_at")
    private LocalDateTime leaveRequestedAt;
    @Column(name = "no_show_penalty_points")
    private Integer noShowPenaltyPoints = 0;
}
