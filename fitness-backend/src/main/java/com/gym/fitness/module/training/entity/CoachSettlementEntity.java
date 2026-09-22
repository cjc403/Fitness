package com.gym.fitness.module.training.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="coach_settlement",uniqueConstraints=@UniqueConstraint(columnNames={"venue_id","coach_id","settlement_month"}))
@Getter @Setter @NoArgsConstructor
public class CoachSettlementEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="venue_id",nullable=false) private Long venueId;
    @Column(name="coach_id",nullable=false) private Long coachId;
    @Column(name="settlement_month",nullable=false,length=7) private String month;
    @Column(name="lesson_count",nullable=false) private Integer lessonCount;
    @Column(name="service_amount",nullable=false,precision=12,scale=2) private BigDecimal serviceAmount;
    @Column(name="commission_amount",nullable=false,precision=12,scale=2) private BigDecimal commissionAmount;
    @Column(nullable=false,length=20) private String status;
    @Column(name="created_by",nullable=false) private Long createdBy;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="approved_by") private Long approvedBy;
    @Column(name="approved_at") private LocalDateTime approvedAt;
}
