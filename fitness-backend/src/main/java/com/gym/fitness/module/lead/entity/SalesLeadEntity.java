package com.gym.fitness.module.lead.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="sales_lead",uniqueConstraints=@UniqueConstraint(name="uk_lead_venue_phone",columnNames={"venue_id","phone"}))
@Getter @Setter @NoArgsConstructor
public class SalesLeadEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private long version;
    @Column(nullable=false,length=50) private String name;
    @Column(nullable=false,length=20) private String phone;
    @Column(nullable=false,length=20) private String source;
    @Column(nullable=false,length=20) private String intent;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(name="venue_id",nullable=false) private Long venueId;
    @Column(nullable=false,length=30) private String stage;
    @Column(length=1000) private String note;
    @Column(name="next_follow_at") private LocalDateTime nextFollowAt;
    @Column(name="visited_at") private LocalDateTime visitedAt;
    @Column(name="trial_completed_at") private LocalDateTime trialCompletedAt;
    @Column(name="converted_member_id") private Long convertedMemberId;
    @Column(name="converted_order_id",unique=true) private Long convertedOrderId;
    @Column(name="converted_at") private LocalDateTime convertedAt;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
}
