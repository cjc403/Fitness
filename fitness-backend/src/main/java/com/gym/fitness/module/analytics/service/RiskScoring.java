package com.gym.fitness.module.analytics.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 会员流失风险评分（**纯规则，不依赖大模型**，便于解释与复核）。
 *
 * <p>口径（全部可解释，界面上也会展示原因）：<br>
 * ① 最近到店距今：≤14 天 +0；15–30 天 +30；31–60 天 +60；&gt;60 天 +85；从未到店按加入天数同口径计算。<br>
 * ② 近 60 天到店次数：0 次 +10；1–3 次 +5；≥4 次 +0。<br>
 * ③ 权益状态：没有任何 ACTIVE 权益 +5（到期没续费的人更容易流失）。<br>
 * 合计上限 100：≥80 高风险、50–79 中风险、其余低风险。</p>
 */
public final class RiskScoring {
    private RiskScoring() {
    }

    public record Score(int value, String level, String reason) {
    }

    public static Score score(LocalDate lastVisit, LocalDate joinedAt, long visits60, boolean hasActiveEntitlement,
                             LocalDate today) {
        LocalDate reference = lastVisit != null ? lastVisit : joinedAt;
        long days = reference == null ? 999 : Math.max(0, ChronoUnit.DAYS.between(reference, today));
        int value = days <= 14 ? 0 : days <= 30 ? 30 : days <= 60 ? 60 : 85;
        String visitText = lastVisit == null ? "从未到店" : "最近到店 " + days + " 天前";

        if (visits60 == 0) value += 10;
        else if (visits60 <= 3) value += 5;

        String reason = visitText + " · 近 60 天到店 " + visits60 + " 次";
        if (!hasActiveEntitlement) {
            value += 5;
            reason += " · 无有效权益";
        }
        value = Math.min(100, value);
        String level = value >= 80 ? "HIGH" : value >= 50 ? "MEDIUM" : "LOW";
        return new Score(value, level, reason);
    }
}
