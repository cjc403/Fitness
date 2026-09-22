package com.gym.fitness.module.finance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
@Entity @Table(name="entitlement_term_period") @Getter @Setter
public class EntitlementTermPeriodEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true) private Long fundingId;
    @Column(nullable=false) private Long entitlementId;
    @Column(nullable=false) private long totalCents;
    @Column(nullable=false) private int totalDays;
    @Column(nullable=false) private int consumedDays;
    @Column(nullable=false) private LocalDate startDate;
    @Column(nullable=false) private LocalDate nextServiceDate;
}

