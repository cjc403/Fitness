package com.gym.fitness.module.commercial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "entitlement_operation")
@Getter @Setter @NoArgsConstructor
public class EntitlementOperationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "entitlement_id", nullable = false) private Long entitlementId;
    @Column(name = "operation_type", nullable = false, length = 30) private String operationType;
    @Column(name = "before_status", length = 30) private String beforeStatus;
    @Column(name = "after_status", length = 30) private String afterStatus;
    @Column(precision = 12, scale = 2) private BigDecimal amount;
    @Column(length = 500) private String note;
    @Column(name = "operator_id") private Long operatorId;
    @CreationTimestamp @Column(name = "operated_at", updatable = false) private LocalDateTime operatedAt;
}
