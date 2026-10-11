package com.gym.fitness.module.lead.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="lead_trial") @Getter @Setter @NoArgsConstructor
public class LeadTrialEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="lead_id",nullable=false) private Long leadId;
    @Column(name="venue_id",nullable=false) private Long venueId;
    @Column(name="host_id",nullable=false) private Long hostId;
    @Column(name="host_name",nullable=false,length=50) private String hostName;
    @Column(name="starts_at",nullable=false) private LocalDateTime startsAt;
    @Column(name="ends_at",nullable=false) private LocalDateTime endsAt;
    @Column(nullable=false,length=1000) private String content;
    @Column(nullable=false,length=20) private String status;
    @Column(name="result_note",length=1000) private String resultNote;
    @Column(name="recorded_at") private LocalDateTime recordedAt;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
}
