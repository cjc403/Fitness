package com.gym.fitness.module.review.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 课程评价。一条预约最多评价一次（booking_id 唯一约束，数据库层兜底）。
 * 只有状态为 ATTENDED（已签到出席）的预约才允许评价，保证评价来自真实到课。
 */
@Entity
@Table(name = "course_review")
@Getter
@Setter
@NoArgsConstructor
public class CourseReviewEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;
    @Column(name = "coach_id")
    private Long coachId;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
    /** 1–5 星。 */
    @Column(nullable = false)
    private Integer rating;
    /** 逗号分隔的标签快照，例如「教练专业,场地整洁」。 */
    @Column(length = 200)
    private String tags;
    @Column(length = 500)
    private String content;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
