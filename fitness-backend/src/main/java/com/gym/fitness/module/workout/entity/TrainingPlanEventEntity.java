package com.gym.fitness.module.workout.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="training_plan_event",uniqueConstraints=@UniqueConstraint(columnNames={"member_id","actor_id","idempotency_key"})) @Getter @Setter @NoArgsConstructor
public class TrainingPlanEventEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="member_id",nullable=false) private Long memberId;
 @Column(name="actor_id",nullable=false) private Long actorId;
 @Column(name="plan_id",nullable=false) private Long planId;
 @Column(name="session_id") private Long sessionId;
 @Column(name="type",nullable=false,length=30) private String type;
 @Column(name="actor_name",nullable=false,length=50) private String actorName;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="reason",length=1000) private String reason;
 @Column(name="idempotency_key",nullable=false,length=80) private String idempotencyKey;
 @Column(name="request_digest",nullable=false,length=64) private String requestDigest;
}
