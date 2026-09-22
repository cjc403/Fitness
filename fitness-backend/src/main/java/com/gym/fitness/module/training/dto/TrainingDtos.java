package com.gym.fitness.module.training.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public final class TrainingDtos {
    private TrainingDtos() {}
    @Data public static class RuleRequest {
        @NotNull private Long coachId;
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) private BigDecimal rate;
    }
    @Data public static class SettlementRequest {
        @NotNull private Long coachId;
        @NotBlank @Pattern(regexp="\\d{4}-(0[1-9]|1[0-2])") private String month;
    }
    @Data public static class ReasonRequest { @NotBlank @Size(max=200) private String reason; }
    public record LessonResponse(Long id,Long bookingId,Long entitlementId,Long memberId,String memberName,Long coachId,String coachName,Long venueId,String courseName,LocalDateTime startTime,LocalDateTime endTime,String status,BigDecimal unitPrice,BigDecimal commissionRate,BigDecimal commissionAmount,Long settlementId,LocalDateTime submittedAt,LocalDateTime confirmedAt,String note) {}
    public record RuleResponse(Long coachId,String coachName,Long venueId,BigDecimal rate,boolean defaultRule,LocalDateTime updatedAt) {}
    public record SettlementResponse(Long id,Long coachId,String coachName,Long venueId,String month,Integer lessonCount,BigDecimal serviceAmount,BigDecimal commissionAmount,String status,LocalDateTime createdAt,LocalDateTime approvedAt) {}

    /**
     * 本门店"私教消课能不能跑起来"的自检结果。用于在管理端面板上直接告诉操作者缺哪一环：
     * 课程 → 排课 → 绑定教练 → 会员有可用课包，缺任何一环这条链路都不会产生记录。
     */
    public record PrivateReadiness(int privateCourses,int scheduledPrivateLessons,int lessonsWithCoach,int usablePackages) {}
}
