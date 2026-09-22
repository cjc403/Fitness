package com.gym.fitness.module.finance.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;
@Entity @Table(name="entitlement_value_event") @Getter @Setter
public class EntitlementValueEventEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long entitlementId;
    @Column(nullable=false) private Long venueId;
    @Column(nullable=false,unique=true,length=150) private String businessKey;
    @Column(nullable=false,length=40) private String eventType;
    @Column(nullable=false) private long deltaCents;
    @Column(nullable=false) private long balanceAfterCents;
    @Column(nullable=false) private boolean verified;
    private Integer unitsDelta;
    @Column(nullable=false,columnDefinition="text") private String allocationsJson="{}";
    @Column(nullable=false,columnDefinition="text") private String detailsJson="{}";
    private Long operatorId;
    @Column(nullable=false) private LocalDateTime occurredAt=LocalDateTime.now();
}
