package com.gym.fitness.module.course.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "course_schedule")
@Getter @Setter @NoArgsConstructor
public class CourseScheduleEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "course_id", nullable = false)
    private Long courseId;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", insertable = false, updatable = false)
    private CourseEntity course;
    @Column(name = "coach_id")
    private Long coachId;
    @Column(name = "coach_name", length = 50)
    private String coachName;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
    @Column(name = "scheduled_start_time", nullable = false)
    private LocalDateTime scheduledStartTime;
    @Column(name = "scheduled_end_time", nullable = false)
    private LocalDateTime scheduledEndTime;
    @Column(name = "max_students")
    private Integer maxStudents;
    @Column(name = "enrolled_count")
    private Integer enrolledCount;
    @Column(length = 20)
    private String status;
    @Column(name = "created_by")
    private Long createdBy;
}
