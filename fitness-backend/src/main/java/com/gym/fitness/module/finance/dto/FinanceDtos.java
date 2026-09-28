package com.gym.fitness.module.finance.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
public final class FinanceDtos {
    private FinanceDtos(){}
    public record Source(@NotNull Long orderId,@NotNull BigDecimal unusedAmount,String productType,Integer totalUnits,LocalDate startDate){}
    public record ReconcileRequest(@NotNull Long version,Integer remainingUses,@NotBlank @Size(max=1000) String reason,@NotEmpty @Valid List<Source> sources,@Size(max=1000) String transferEvidence){}
    public record TransferEvidenceRequest(@NotNull Long version,@NotBlank @Size(max=1000) String reason,@NotBlank @Size(max=1000) String transferEvidence){}
    public record RefundRequest(@NotNull Long entitlementId,@NotNull Long version,@NotBlank @Size(max=1000) String reason,@NotBlank @Size(max=100) String idempotencyKey){}
    public record DecisionRequest(@NotBlank @Size(max=1000) String reason){}
    public record PayRequest(@NotNull BigDecimal amount,@NotBlank @Size(max=30) String channel,@NotBlank @Size(max=100) String externalReference,@NotNull LocalDateTime occurredAt,@NotBlank @Size(max=1000) String note){}
    public record Funding(Long orderId,String orderNo,BigDecimal unusedAmount){}
    public record Quote(Long entitlementId,String verificationStatus,String productType,BigDecimal refundableAmount,Integer remainingUses,Integer remainingServiceDays,long version,List<String> blockedReasons,Long activeRefundId,List<Funding> funding){}

    /** 挡住退款的一条未完成预约（或待签私教课）；cancellable=true 表示可以一键取消。 */
    public record Blocker(String type,Long bookingId,Long lessonId,String label,String detail,boolean cancellable){}

    /** 一键取消的结果：cancelled 已取消、skipped 需人工处理、remainingBlocks 是处理完仍存在的阻断原因。 */
    public record ClearBlockers(List<Item> cancelled,List<Item> skipped,List<String> remainingBlocks){
        public record Item(String label,String detail){}
    }
    public record Allocation(Long orderId,String orderNo,BigDecimal amount){}
    public record Audit(String action,String reason,LocalDateTime occurredAt){}
    public record Refund(Long id,String refundNo,Long entitlementId,String entitlementNo,Long memberId,String memberName,Long venueId,String status,BigDecimal amount,String reason,Long requestedBy,LocalDateTime requestedAt,Long approvedBy,LocalDateTime approvedAt,LocalDateTime paidAt,String decisionReason,List<Allocation> allocations,List<Audit> audit){}
    public record MemberRefund(Long id,String refundNo,String entitlementNo,String status,BigDecimal amount,LocalDateTime requestedAt,LocalDateTime approvedAt,LocalDateTime paidAt){}
    public record Channel(String channel,BigDecimal receipts,BigDecimal refunds,BigDecimal netCash){}
    public record Difference(String type,String subjectType,Long subjectId,Long entitlementId,Long orderId,Long paymentTransactionId,Long refundId,BigDecimal accountBalance,BigDecimal eventBalance,String message){
        public Difference(String type,Long entitlementId,BigDecimal accountBalance,BigDecimal eventBalance,String message){
            this(type,"ENTITLEMENT",entitlementId,entitlementId,null,null,null,accountBalance,eventBalance,message);
        }
    }
    public record Report(LocalDate from,LocalDate to,BigDecimal receipts,BigDecimal refunds,BigDecimal netCash,BigDecimal unusedVerifiedValue,int unverifiedEntitlements,BigDecimal serviceConsumed,List<Channel> channels,List<Difference> differences,LocalDate valuationDate){}
}
