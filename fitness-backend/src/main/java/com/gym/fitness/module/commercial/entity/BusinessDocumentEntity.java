package com.gym.fitness.module.commercial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "business_document")
@Getter @Setter @NoArgsConstructor
public class BusinessDocumentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "document_no", nullable = false, unique = true, length = 50) private String documentNo;
    @Column(name = "document_type", nullable = false, length = 20) private String documentType;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false, length = 200) private String title;
    @Column(name = "tax_no", length = 50) private String taxNo;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "issued_at") private LocalDateTime issuedAt;
    @Column(name = "created_by") private Long createdBy;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
