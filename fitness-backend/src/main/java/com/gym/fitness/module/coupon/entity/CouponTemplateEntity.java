package com.gym.fitness.module.coupon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 优惠券模板（活动/券的定义）。金额一律用「分」存整数，与项目既有资金口径一致。
 *
 * <p>两种类型：{@code FULL_REDUCTION} 满减（满 thresholdCents 减 discountCents）、
 * {@code DISCOUNT} 折扣（按 discountRate 打折，如 0.9000 = 九折）。</p>
 */
@Entity
@Table(name = "coupon_template")
@Getter
@Setter
@NoArgsConstructor
public class CouponTemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "coupon_type", nullable = false, length = 20)
    private String couponType;
    /** 使用门槛（分）；0 表示无门槛。 */
    @Column(name = "threshold_cents", nullable = false)
    private Long thresholdCents = 0L;
    /** 满减金额（分）。 */
    @Column(name = "discount_cents", nullable = false)
    private Long discountCents = 0L;
    /** 折扣率（如 0.9000）；仅 DISCOUNT 类型使用。 */
    @Column(name = "discount_rate", precision = 5, scale = 4)
    private BigDecimal discountRate;
    /** 发放总量；0 表示不限量。 */
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity = 0;
    @Column(name = "issued_quantity", nullable = false)
    private Integer issuedQuantity = 0;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 200)
    private String note;
    @Column(name = "created_by")
    private Long createdBy;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
