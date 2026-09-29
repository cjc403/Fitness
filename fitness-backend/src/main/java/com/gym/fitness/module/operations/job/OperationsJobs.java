package com.gym.fitness.module.operations.job;

import com.gym.fitness.module.operations.service.OperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务入口（很薄，逻辑都在 {@link OperationsService}，便于直接测试）。
 *
 * <p>可用 {@code fitness.operations.enabled=false} 整体关闭；三条 cron 也都可以用配置覆盖。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fitness.operations.enabled", havingValue = "true", matchIfMissing = true)
public class OperationsJobs {
    private final OperationsService operations;

    /** 过期未核销预约检查：每天 07:10。 */
    @Scheduled(cron = "${fitness.operations.overdue-cron:0 10 7 * * *}", zone = "Asia/Shanghai")
    public void overdueBookings() {
        try {
            log.info("定时任务·过期预约：{}", operations.handleOverdueBookings());
        } catch (Exception e) {
            log.error("定时任务·过期预约执行失败", e);
        }
    }

    /** 过期门禁令牌清理：每天 03:30。 */
    @Scheduled(cron = "${fitness.operations.cleanup-cron:0 30 3 * * *}", zone = "Asia/Shanghai")
    public void cleanupExpiredGrants() {
        try {
            log.info("定时任务·令牌清理：{}", operations.cleanupExpiredGrants());
        } catch (Exception e) {
            log.error("定时任务·令牌清理执行失败", e);
        }
    }

    /** 权益到期提醒：每天 09:00。 */
    @Scheduled(cron = "${fitness.operations.reminder-cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    public void remindExpiringEntitlements() {
        try {
            log.info("定时任务·到期提醒：{}", operations.remindExpiringEntitlements());
        } catch (Exception e) {
            log.error("定时任务·到期提醒执行失败", e);
        }
    }
}
