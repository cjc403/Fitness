package com.gym.fitness.module.training.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="private_lesson", uniqueConstraints=@UniqueConstraint(columnNames="booking_id"))
@Getter @Setter @NoArgsConstructor
public class PrivateLessonEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="booking_id",nullable=false) private Long bookingId;
    @Column(name="schedule_id",nullable=false) private Long scheduleId;
    @Column(name="entitlement_id",nullable=false) private Long entitlementId;
    @Column(name="member_id",nullable=false) private Long memberId;
    @Column(name="coach_id",nullable=false) private Long coachId;
    @Column(name="venue_id",nullable=false) private Long venueId;
    @Column(nullable=false,length=20) private String status;
    @Column(name="unit_price",nullable=false,precision=12,scale=2) private BigDecimal unitPrice;
    @Column(name="commission_rate",precision=5,scale=2) private BigDecimal commissionRate;
    @Column(name="commission_amount",precision=12,scale=2) private BigDecimal commissionAmount;
    @Column(name="settlement_id") private Long settlementId;
    @Column(name="reserved_at",nullable=false) private LocalDateTime reservedAt;
    @Column(name="submitted_at") private LocalDateTime submittedAt;
    @Column(name="confirmed_at") private LocalDateTime confirmedAt;
    @Column(name="released_at") private LocalDateTime releasedAt;
    @Column(name="reversed_at") private LocalDateTime reversedAt;
    @Column(length=500) private String note;
}
