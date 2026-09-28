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
                               LocalDateTime createdAt) {
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
                                LocalDateTime nextFollowAt, boolean due) {
    }

    public record AdvisorWorkbench(String advisorName, AdvisorSummary summary, List<AdvisorMember> members) {
    }

    @Data
    public static class FollowUpRequest {
        /** CALL | WECHAT | VISIT | OTHER */
        @NotBlank private String followType;
        @NotBlank @Size(max = 500) private String content;
        private LocalDateTime nextFollowAt;
    }
}
