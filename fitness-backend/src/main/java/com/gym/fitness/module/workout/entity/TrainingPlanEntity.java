package com.gym.fitness.module.workout.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="training_plan") @Getter @Setter @NoArgsConstructor
public class TrainingPlanEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="member_id",nullable=false) private Long memberId;
 @Column(name="coach_id",nullable=false) private Long coachId;
 @Column(name="venue_id",nullable=false) private Long venueId;
 @Column(name="title",nullable=false,length=100) private String title;
 @Column(name="goal",nullable=false,length=20) private String goal;
 @Column(name="start_date",nullable=false) private LocalDate startDate;
 @Column(name="end_date",nullable=false) private LocalDate endDate;
 @Column(name="note",length=1000) private String note;
 @Column(name="status",nullable=false,length=20) private String status;
 @Column(name="version",nullable=false) private Long version;
 @Column(name="active_member_key",unique=true,length=80) private String activeMemberKey;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
