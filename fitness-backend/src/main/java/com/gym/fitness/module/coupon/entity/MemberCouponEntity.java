package com.gym.fitness.module.coupon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会员持有的一张券。状态 {@code UNUSED → USED}；过期由读取时按 validUntil 判定为 {@code EXPIRED}。
 * 抵扣金额与所用订单记在这里，便于对账与退款追溯。
 */
@Entity
@Table(name = "member_coupon")
@Getter
@Setter
@NoArgsConstructor
public class MemberCouponEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(name = "venue_id", nullable = false)
    private Long venueId;
    @Column(nullable = false, unique = true, length = 40)
    private String code;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;
    @Column(name = "used_at")
    private LocalDateTime usedAt;
    @Column(name = "used_order_id")
    private Long usedOrderId;
    /** 本张券实际抵扣了多少分（下单时写入，作为历史快照）。 */
    @Column(name = "discount_cents", nullable = false)
    private Long discountCents = 0L;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;
}
