package com.gym.fitness.module.finance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
@Entity @Table(name="entitlement_funding") @Getter @Setter
public class EntitlementFundingEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long entitlementId;
    @Column(nullable=false,unique=true) private Long orderId;
    @Column(nullable=false) private Long venueId;
    @Column(nullable=false) private long receivedCents;
    @Column(nullable=false) private long remainingCents;
    @Column(nullable=false,length=40) private String productType;
    private Integer totalUnits;
    @Column(nullable=false) private boolean verified;
    @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
}

