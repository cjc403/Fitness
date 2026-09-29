package com.gym.fitness.module.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    /** 经营概览。活跃/沉默/流失按"最近到店距今"天数划分：≤30 活跃、31–60 沉默、>60 流失。 */
    public record Overview(LocalDate from, LocalDate to, long newMembers, long totalMembers,
                           long activeMembers, long silentMembers, long churnedMembers,
                           long totalAttendances, BigDecimal revenue, long highRiskMembers) {
    }

    /** 教练绩效：私教课时与产值取已签课（CONFIRMED）记录，团课取已签到预约，评价为累计。 */
    public record CoachRank(Long coachId, String coachName, long privateLessons, BigDecimal privateRevenue,
                            long groupAttendances, long reviews, BigDecimal averageRating) {
    }

    /** 会员消费与到店 + 流失风险。 */
    public record MemberRisk(Long memberId, String memberNo, String realName, String phone,
                             Long advisorId, String advisorName, LocalDate lastVisit, long visits60,
                             BigDecimal spending, int riskScore, String riskLevel, String reason) {
    }

    public record MemberAnalytics(List<MemberRisk> members, long highRisk, long mediumRisk) {
    }
}
