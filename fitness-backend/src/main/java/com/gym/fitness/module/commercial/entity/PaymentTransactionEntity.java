package com.gym.fitness.module.commercial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transaction")
@Getter @Setter @NoArgsConstructor
public class PaymentTransactionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "transaction_no", nullable = false, unique = true, length = 50) private String transactionNo;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "transaction_type", nullable = false, length = 20) private String transactionType;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "payment_method", nullable = false, length = 30) private String paymentMethod;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "external_reference", length = 100) private String externalReference;
    @Column(name = "operator_id") private Long operatorId;
    @Column(name = "occurred_at", updatable = false) private LocalDateTime occurredAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
}
