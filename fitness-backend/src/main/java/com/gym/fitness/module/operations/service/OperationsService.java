package com.gym.fitness.module.operations.service;

import com.gym.fitness.module.booking.service.BookingOverdueService;
import com.gym.fitness.module.commercial.entity.MemberEntitlementEntity;
import com.gym.fitness.module.commercial.repository.AccessGrantRepository;
import com.gym.fitness.module.commercial.repository.MemberEntitlementRepository;
import com.gym.fitness.module.notice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 运营自动化：把三件"没人做就会一直积压"的事交给定时任务。
 *
 * <p>① 过期未核销预约：**默认只提醒**（写日志 + 首页已有提醒），只有显式打开开关才自动核销——
 * 因为"自动给会员记爽约"属于业务政策，不该由系统默认决定。
 * ② 过期门禁令牌清理：一次性令牌用完即失效，过期的可以清掉（保留最近若干天便于排查）。
 * ③ 权益到期提醒：到期前 7/3/1 天各推送一次站内通知，靠 refType 区分档位去重。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationsService {
    private final BookingOverdueService overdueBookings;
    private final AccessGrantRepository grants;
    private final com.gym.fitness.module.auth.repository.RevokedAuthSessionRepository revokedSessions;
    private final MemberEntitlementRepository entitlements;
    private final NotificationService notifications;

    @Value("${fitness.operations.auto-resolve-overdue:false}")
    private boolean autoResolveOverdue;
    @Value("${fitness.operations.token-retention-days:1}")
    private int tokenRetentionDays;
    @Value("${fitness.operations.expiry-reminder-days:7}")
    private int expiryReminderDays;

    /** 过期未核销预约：默认只提醒。 */
    public String handleOverdueBookings() {
        var list = overdueBookings.overdueAll();
        if (list.isEmpty()) return "没有过期未核销的预约";
        if (!autoResolveOverdue) {
            log.warn("运营自检：有 {} 条预约已结束但未核销，会挡住会员退款；默认只提醒，请在「预约与签到」点「一键核销」", list.size());
            return "发现 " + list.size() + " 条过期未核销预约（当前为只提醒模式，未自动处理）";
        }
        var result = overdueBookings.resolveAll();
        log.info("运营自检：自动核销完成，爽约 {} 条、候补失效 {} 条、需人工 {} 条",
                result.noShow().size(), result.cancelled().size(), result.skipped().size());
        return "自动核销完成：爽约 " + result.noShow().size() + " 条、候补失效 " + result.cancelled().size()
                + " 条、需人工 " + result.skipped().size() + " 条";
    }

    /**
     * 清理过期门禁令牌。
     *
     * <p>**必须加 @Transactional**：Spring Data 的派生 deleteBy 要求有事务，否则运行期抛
     * "no transaction is in progress"——定时任务会在凌晨静默失败。</p>
     */
    @org.springframework.transaction.annotation.Transactional
    public String cleanupExpiredGrants() {
        int retention = Math.max(1, tokenRetentionDays);
        long removed = grants.deleteByExpiresAtBefore(LocalDateTime.now().minusDays(retention));
        if (removed > 0) log.info("运营自检：清理过期门禁令牌 {} 条（保留最近 {} 天）", removed, retention);
        int revokedRemoved = revokedSessions.deleteExpiredBefore(java.time.Instant.now());
        return "清理过期门禁令牌 " + removed + " 条；过期会话撤销记录 " + revokedRemoved + " 条";
    }

    /** 权益到期提醒（7/3/1 天各一次）。 */
    public String remindExpiringEntitlements() {
        LocalDate today = LocalDate.now();
        int sent = 0, skipped = 0;
        for (MemberEntitlementEntity e : entitlements.findAllByOrderByIdDesc()) {
            if (!"ACTIVE".equals(e.getStatus()) || e.getValidUntil() == null) continue;
            long days = ChronoUnit.DAYS.between(today, e.getValidUntil());
            if (days < 0 || days > expiryReminderDays) continue;
            String level = days <= 1 ? "1" : days <= 3 ? "3" : String.valueOf(expiryReminderDays);
            String refType = "ENTITLEMENT_EXPIRY_" + level;
            if (notifications.existsForMember(e.getMemberId(), refType, e.getId())) {
                skipped++;
                continue;
            }
            notifications.notifyMember(e.getMemberId(), e.getVenueId(), "MEMBERSHIP", "会员权益即将到期",
                    "权益 " + e.getEntitlementNo() + " 将于 " + e.getValidUntil() + " 到期（剩余 " + days
                            + " 天），续费或到店咨询可继续使用。", refType, e.getId());
            sent++;
        }
        if (sent > 0) log.info("运营自检：发出权益到期提醒 {} 条", sent);
        return "权益到期提醒：新发 " + sent + " 条、已发过跳过 " + skipped + " 条";
    }
}
