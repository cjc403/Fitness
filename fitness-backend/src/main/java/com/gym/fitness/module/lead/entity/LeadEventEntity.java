package com.gym.fitness.module.lead.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="lead_event",uniqueConstraints=@UniqueConstraint(name="uk_lead_event_actor_key",columnNames={"actor_id","request_key"}))
@Getter @Setter @NoArgsConstructor
public class LeadEventEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="lead_id",nullable=false) private Long leadId;
    @Column(name="actor_id",nullable=false) private Long actorId;
    @Column(name="actor_name",nullable=false,length=50) private String actorName;
    @Column(nullable=false,length=30) private String type;
    @Column(nullable=false,length=1100) private String content;
    @Column(length=30) private String outcome;
    @Column(name="next_follow_at") private LocalDateTime nextFollowAt;
    @Column(name="request_key",nullable=false,length=100) private String requestKey;
    @Column(name="request_fingerprint",nullable=false,length=64) private String requestFingerprint;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
}
