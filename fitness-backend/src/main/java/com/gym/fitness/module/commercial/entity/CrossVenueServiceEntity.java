package com.gym.fitness.module.commercial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 跨店服务分摊记录：卡在 A 店卖、服务在 B 店提供时记一笔，供内部结算。
 *
 * <p>只记账，不动钱；退款与价值账仍按发卡门店。</p>
 */
@Entity
@Table(name = "cross_venue_service", uniqueConstraints = @UniqueConstraint(columnNames = {"ref_type", "ref_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CrossVenueServiceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "entitlement_id")
    private Long entitlementId;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    /** 发卡（收款）门店。 */
    @Column(name = "issuer_venue_id", nullable = false)
    private Long issuerVenueId;
    /** 实际提供服务门店。 */
    @Column(name = "serving_venue_id", nullable = false)
    private Long servingVenueId;
    @Column(name = "ref_type", nullable = false, length = 30)
    private String refType;
    @Column(name = "ref_id", nullable = false)
    private Long refId;
    /** 该次服务对应的金额（元）。 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @CreationTimestamp
    @Column(name = "occurred_at", updatable = false)
    private LocalDateTime occurredAt;
    @Column(length = 200)
    private String note;
}
