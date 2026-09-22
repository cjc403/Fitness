package com.gym.fitness.module.finance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
@Entity @Table(name="refund_application",uniqueConstraints={@UniqueConstraint(columnNames={"requested_by","idempotency_key"}),@UniqueConstraint(columnNames={"channel","external_reference"})}) @Getter @Setter
public class RefundApplicationEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=50) private String refundNo;
    @Column(nullable=false) private Long entitlementId;
    @Column(unique=true) private Long activeEntitlementId;
    @Column(nullable=false) private Long memberId;
    @Column(nullable=false) private Long venueId;
    @Column(nullable=false,length=30) private String status;
    @Column(nullable=false) private long amountCents;
    @Column(nullable=false) private long valueVersion;
    private Integer remainingUsesSnapshot;
    @Column(nullable=false,length=1000) private String reason;
    @Column(nullable=false) private Long requestedBy;
    @Column(nullable=false,length=100) private String idempotencyKey;
    @Column(nullable=false,length=100) private String requestFingerprint;
    @Column(nullable=false) private LocalDateTime requestedAt=LocalDateTime.now();
    private Long approvedBy;
    private LocalDateTime approvedAt;
    @Column(length=1000) private String decisionReason;
    private Long paidBy;
    private LocalDateTime paidAt;
    @Column(length=30) private String channel;
    @Column(length=100) private String externalReference;
    @Column(length=1000) private String paymentNote;
}

