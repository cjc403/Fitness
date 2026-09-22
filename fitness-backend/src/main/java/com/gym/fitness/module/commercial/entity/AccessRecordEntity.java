package com.gym.fitness.module.commercial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_record")
@Getter @Setter @NoArgsConstructor
public class AccessRecordEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id") private Long memberId;
    @Column(name = "entitlement_id") private Long entitlementId;
    @Column(name = "venue_id", nullable = false) private Long venueId;
    @Column(name = "access_token_masked", length = 20) private String accessTokenMasked;
    @Column(nullable = false, length = 10) private String direction;
    @Column(nullable = false, length = 20) private String result;
    @Column(length = 200) private String reason;
    @Column(name = "operator_id") private Long operatorId;
    @CreationTimestamp @Column(name = "scanned_at", updatable = false) private LocalDateTime scannedAt;
}
