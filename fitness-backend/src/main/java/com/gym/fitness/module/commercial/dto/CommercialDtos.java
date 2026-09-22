package com.gym.fitness.module.commercial.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CommercialDtos {
    private CommercialDtos() {}

    @Data
    public static class ProductRequest {
        @NotBlank private String name;
        @NotBlank private String category;
        @NotBlank private String productType;
        private String description;
        @NotNull @Min(1) private Integer validityDays;
        @Min(1) private Integer totalUses;
        private Boolean accessAllowed;
        @NotNull @DecimalMin("0.00") private BigDecimal price;
        private Long venueId;
        private String status;
    }

    @Data
    public static class SaleRequest {
        @NotNull private Long memberId;
        @NotNull private Long productId;
        @NotBlank private String paymentMethod;
        @DecimalMin("0.01") private BigDecimal paidAmount;
        private String remark;
        /** 同一操作者+同一幂等键只会落一单，防止连点或重试重复开卡。 */
        @NotBlank @Size(max = 100) private String idempotencyKey;
    }

    @Data
    public static class RenewalRequest {
        @Min(1) private Integer additionalDays;
        @Min(1) private Integer additionalUses;
        @NotNull @DecimalMin("0.01") private BigDecimal amount;
        @NotBlank private String paymentMethod;
        private String note;
    }

    @Data
    public static class TransferRequest {
        @NotNull private Long targetMemberId;
        private String note;
    }

    @Data
    public static class RefundRequest {
        @NotNull @DecimalMin("0.01") private BigDecimal amount;
        @NotBlank private String paymentMethod;
        private String reason;
    }

    @Data
    public static class NoteRequest { private String note; }

    @Data
    public static class InvoiceRequest {
        @NotBlank private String title;
        private String taxNo;
    }

    @Data
    public static class AccessScanRequest {
        @NotBlank private String token;
        private String direction;
    }

    public record ProductResponse(Long id, String name, String category, String productType,
                                  String description, Integer validityDays, Integer totalUses,
                                  Boolean accessAllowed, BigDecimal price, Long venueId, String status) {}

    /**
     * 会员端会籍商城的商品视图。为了让会员端与员工端看到的是同一套在售商品（含跨门店），
     * 这里一并返回场馆名，并由服务端判定 purchasable——会员只能购买本人所属门店的商品
     * （createSale 的"会员与商品必须属于同一场馆"规则），其余门店的商品只做展示。
     */
    public record MemberProductView(Long id, String name, String productType, String description,
                                    Integer validityDays, Integer totalUses, BigDecimal price,
                                    Long venueId, String venueName, boolean purchasable) {}

    public record OrderResponse(Long id, String orderNo, String orderType, Long memberId,
                                String memberNo, String memberName, Long productId, String productName,
                                Long venueId, BigDecimal originalAmount, BigDecimal paidAmount,
                                BigDecimal refundedAmount, String paymentMethod, String status,
                                String remark, LocalDateTime paidAt, LocalDateTime createdAt) {}

    public record EntitlementResponse(Long id, String entitlementNo, Long memberId, String memberNo,
                                      String memberName, Long productId, String productName, String productType,
                                      Boolean accessAllowed, Long sourceOrderId, Long venueId, LocalDate validFrom,
                                      LocalDate validUntil, Integer totalUses, Integer remainingUses, String status,
                                      LocalDateTime frozenAt, Integer accumulatedFrozenDays, Integer daysRemaining,
                                      String reminderLevel, Integer reservedUses, Integer availableUses) {}

    public record PaymentResponse(Long id, String transactionNo, Long orderId, String orderNo,
                                  String transactionType, BigDecimal amount, String paymentMethod,
                                  String status, LocalDateTime occurredAt) {}

    public record DocumentResponse(Long id, String documentNo, String documentType, Long orderId,
                                   String orderNo, String memberName, String title, String taxNo,
                                   BigDecimal amount, String status, LocalDateTime issuedAt) {}

    public record AccessCodeResponse(String memberNo, String memberName, String token,
                                     String qrPayload, LocalDateTime generatedAt,
                                     LocalDateTime expiresAt, long ttlSeconds) {}

    public record AccessResponse(Long id, Long memberId, String memberNo, String memberName,
                                 Long entitlementId, String entitlementNo, Long venueId,
                                 String direction, String result, String reason, LocalDateTime scannedAt) {}
}
