package com.gym.fitness.module.commercial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "membership_product", uniqueConstraints = @UniqueConstraint(columnNames = {"venue_id", "name"}))
@Getter @Setter @NoArgsConstructor
public class MembershipProductEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 20) private String category;
    @Column(name = "product_type", nullable = false, length = 30) private String productType;
    @Column(length = 500) private String description;
    @Column(name = "validity_days", nullable = false) private Integer validityDays;
    @Column(name = "total_uses") private Integer totalUses;
    @Column(name = "access_allowed", nullable = false) private Boolean accessAllowed = true;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Column(name = "venue_id", nullable = false) private Long venueId;
    @Column(nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "created_by") private Long createdBy;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private LocalDateTime updatedAt;
}
