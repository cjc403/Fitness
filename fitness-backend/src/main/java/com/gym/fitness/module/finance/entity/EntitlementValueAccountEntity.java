package com.gym.fitness.module.finance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
@Entity @Table(name="entitlement_value_account") @Getter @Setter
public class EntitlementValueAccountEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true) private Long entitlementId;
    @Column(nullable=false) private Long memberId;
    @Column(nullable=false) private Long venueId;
    @Column(nullable=false,length=30) private String verificationStatus="UNVERIFIED";
    @Column(nullable=false,length=40) private String productType="UNKNOWN";
    @Column(nullable=false) private long balanceCents;
    @Column(nullable=false) private long version;
    private Long activeRefundId;
    private LocalDate pausedAt;
    private Integer baselineRemainingUses;
    private Long reconciledBy;
    private LocalDateTime reconciledAt;
    @Column(length=1000) private String reconciliationReason;
    @Column(length=1000) private String transferEvidence;
}

