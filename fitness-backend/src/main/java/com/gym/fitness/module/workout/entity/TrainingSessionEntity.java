package com.gym.fitness.module.workout.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="training_session") @Getter @Setter @NoArgsConstructor
public class TrainingSessionEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="plan_id",nullable=false) private Long planId;
 @Column(name="version",nullable=false) private Long version;
 @Column(name="performed_on",nullable=false) private LocalDate performedOn;
 @Column(name="duration_minutes",nullable=false) private Integer durationMinutes;
 @Column(name="effort") private Integer effort;
 @Column(name="note",length=1000) private String note;
 @Column(name="booking_id") private Long bookingId;
 @Column(name="active_booking_key",unique=true) private Long activeBookingKey;
 @Column(name="course_name",length=100) private String courseName;
 @Column(name="status",nullable=false,length=20) private String status;
 @Column(name="void_reason",length=300) private String voidReason;
 @Column(name="coach_name",nullable=false,length=50) private String coachName;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="feedback_rating") private Integer feedbackRating;
 @Column(name="feedback_content",length=500) private String feedbackContent;
 @Column(name="feedback_at") private LocalDateTime feedbackAt;
}
