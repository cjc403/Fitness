package com.gym.fitness.module.coupon.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CouponDtos {
    private CouponDtos() {
    }

    public record TemplateView(Long id, Long venueId, String name, String couponType, Long thresholdCents,
                               Long discountCents, BigDecimal discountRate, Integer totalQuantity,
                               Integer issuedQuantity, LocalDate validFrom, LocalDate validUntil,
                               String status, String note) {
    }

    public record MemberCouponView(Long id, Long templateId, String templateName, String couponType,
                                   Long thresholdCents, Long discountCents, BigDecimal discountRate,
                                   String code, String status, LocalDate validFrom, LocalDate validUntil,
                                   Long discountCentsUsed, LocalDateTime usedAt, Long usedOrderId,
                                   boolean usable, String unusableReason) {
    }

    /** 用券试算：某会员买某商品时，哪些券可用、各能抵扣多少、折后应付多少。 */
    public record CouponQuote(Long productId, String productName, BigDecimal price, Long priceCents,
                              long usableCount, List<Option> options) {
        public record Option(Long memberCouponId, String name, Long discountCents, BigDecimal payable) {
        }
    }

    @Data
    public static class TemplateRequest {
        @NotBlank @Size(max = 100) private String name;
        /** FULL_REDUCTION 满减 | DISCOUNT 折扣。 */
        @NotBlank private String couponType;
        @Min(0) private Long thresholdCents;
        @Min(0) private Long discountCents;
        private BigDecimal discountRate;
        @Min(0) private Integer totalQuantity;
        @NotNull private LocalDate validFrom;
        @NotNull private LocalDate validUntil;
        private String status;
        @Size(max = 200) private String note;
        /** 仅超级管理员可指定归属门店。 */
        private Long venueId;
    }

    @Data
    public static class IssueRequest {
        @NotEmpty private List<Long> memberIds;
        @Min(1) @Max(10) private Integer quantityPerMember;
    }
}
