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
@Table(name = "sales_order", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sales_order_operator_idempotency", columnNames = {"operator_id", "idempotency_key"})
})
@Getter @Setter @NoArgsConstructor
public class SalesOrderEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_no", nullable = false, unique = true, length = 50) private String orderNo;
    @Column(name = "order_type", nullable = false, length = 20) private String orderType;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "venue_id", nullable = false) private Long venueId;
    @Column(name = "original_amount", nullable = false, precision = 12, scale = 2) private BigDecimal originalAmount;
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2) private BigDecimal paidAmount;
    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2) private BigDecimal refundedAmount = BigDecimal.ZERO;
    @Column(name = "payment_method", length = 30) private String paymentMethod;
    @Column(nullable = false, length = 30) private String status;
    @Column(length = 500) private String remark;
    @Column(name = "operator_id") private Long operatorId;
    @Column(name = "member_coupon_id") private Long memberCouponId;
    /** 本单用券抵扣的分（0 表示未用券）。original_amount 仍是标价，paid_amount 是折后实收。 */
    @Column(name = "discount_cents", nullable = false) private Long discountCents = 0L;
    @Column(name = "idempotency_key", length = 100) private String idempotencyKey;
    @Column(name = "request_fingerprint", length = 128) private String requestFingerprint;
    @Column(name = "paid_at") private LocalDateTime paidAt;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private LocalDateTime updatedAt;
}
