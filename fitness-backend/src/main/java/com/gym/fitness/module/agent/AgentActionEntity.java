package com.gym.fitness.module.agent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="agent_action") @Getter @Setter
public class AgentActionEntity {
 @Id @Column(length=36) private String id;
 @Column(nullable=false) private Long userId;
 @Column(nullable=false) private Long venueId;
 @Column(nullable=false,length=30) private String role;
 @Column(nullable=false,length=64) private String sessionHash;
 @Column(nullable=false,length=60) private String tool;
 @Column(nullable=false,columnDefinition="text") private String argumentsJson;
 @Column(nullable=false,columnDefinition="text") private String businessSnapshot;
 @Column(nullable=false,length=2000) private String summary;
 @Column(nullable=false,length=20) private String status;
 @Column(nullable=false) private LocalDateTime createdAt;
 @Column(nullable=false) private LocalDateTime expiresAt;
 @Column(columnDefinition="text") private String resultJson;
 @Column(length=500) private String error;
 @Version private long version;
}
