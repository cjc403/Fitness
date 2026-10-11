package com.gym.fitness.module.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdvisorDtos {
    private AdvisorDtos() {
    }

    public record FollowUpView(Long id, Long memberId, String memberName, Long staffUserId, String staffName,
                               String followType, String content, LocalDateTime nextFollowAt,
                               LocalDateTime createdAt, String outcome) {
    }

    public record AdvisorView(Long userId, String realName, String username, Long venueId) {
    }

    /** 顾问工作台概况。 */
    public record AdvisorSummary(long myMembers, long newMembersThisMonth, long followUpsThisWeek,
                                 long dueFollowUps, long salesOrders, BigDecimal salesAmount) {
    }

    /** 我的会员一行：档案 + 归属 + 最近跟进 + 是否待跟进。 */
    public record AdvisorMember(Long memberId, String memberNo, String realName, String phone, String status,
                                LocalDateTime lastFollowAt, String lastFollowContent,
                                LocalDateTime nextFollowAt, boolean due,
                                Integer riskScore, String riskLevel) {
    }

    public record AdvisorWorkbench(String advisorName, AdvisorSummary summary, List<AdvisorMember> members) {
    }

    @Data
    public static class FollowUpRequest {
        /** CALL | WECHAT | VISIT | OTHER */
        @NotBlank private String followType;
        @NotBlank @Size(max = 500) private String content;
        private LocalDateTime nextFollowAt;
        @Size(max = 20) private String outcome;
        @Size(max = 64) private String idempotencyKey;
    }

    public record RetentionSummary(long totalMembers, long todayFollowUps, long overdueFollowUps,
                                   long expiring7, long expiring30, long highRisk, long followedThisMonth,
                                   long renewedThisMonth, BigDecimal renewalNetAmount) {}

    public record RetentionMember(Long memberId, String memberNo, String realName, String phone, String status,
                                  Long venueId, String venueName, Long salesUserId, String advisorName,
                                  LocalDateTime lastFollowAt, String lastFollowContent, String lastOutcome,
                                  LocalDateTime nextFollowAt, String followStatus, int riskScore, String riskLevel,
                                  String riskReason, java.time.LocalDate expiresAt, Long expiryDays,
                                  boolean renewedThisMonth, LocalDateTime lastRenewedAt, BigDecimal renewalNetAmount) {}

    public record RetentionAdvisor(Long advisorId, String advisorName, long memberCount, long todayFollowUps,
                                   long overdueFollowUps, long followedThisMonth, long renewedThisMonth) {}

    public record RetentionWorkbench(LocalDateTime asOf, RetentionSummary summary,
                                     List<RetentionMember> members, List<RetentionAdvisor> advisors) {}
}
