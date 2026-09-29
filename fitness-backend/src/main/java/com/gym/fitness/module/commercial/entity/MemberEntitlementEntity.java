package com.gym.fitness.module.commercial.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "member_entitlement")
@Getter @Setter @NoArgsConstructor
public class MemberEntitlementEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "entitlement_no", nullable = false, unique = true, length = 50) private String entitlementNo;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "source_order_id", nullable = false) private Long sourceOrderId;
    @Column(name = "venue_id", nullable = false) private Long venueId;
    /**
     * 适用门店快照（逗号分隔的 venue id）：开卡时把"销售门店 + 商品声明的适用门店"写进来，
     * 之后改商品不影响已售出的卡。空 = 仅限发卡门店（与历史数据一致）。
     */
    @Column(name = "venue_scope", length = 200) private String venueScope;
    @Column(name = "valid_from", nullable = false) private LocalDate validFrom;
    @Column(name = "valid_until", nullable = false) private LocalDate validUntil;
    @Column(name = "total_uses") private Integer totalUses;
    @Column(name = "remaining_uses") private Integer remainingUses;
    @Column(name = "reserved_uses", nullable = false) private Integer reservedUses = 0;
    @Column(name = "lesson_unit_price", precision = 12, scale = 2) private java.math.BigDecimal lessonUnitPrice;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "frozen_at") private LocalDateTime frozenAt;
    @Column(name = "accumulated_frozen_days", nullable = false) private Integer accumulatedFrozenDays = 0;
    @Column(name = "transferred_from_member_id") private Long transferredFromMemberId;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private LocalDateTime updatedAt;
}
