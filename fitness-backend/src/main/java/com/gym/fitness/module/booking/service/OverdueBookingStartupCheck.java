package com.gym.fitness.module.booking.service;

import com.gym.fitness.module.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 启动自检：提醒有多少预约已结束却仍未核销。
 *
 * <p>这类记录会永久挡住会员退款（退款前置条件要求"无未完成服务预约"），
 * 而项目没有定时任务，只能靠人处理，所以在启动日志里主动提醒一次。</p>
 *
 * <p>只写日志、绝不阻断启动：任何异常都吞掉并降级为一条 warn。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueBookingStartupCheck implements ApplicationRunner {
    private final BookingRepository bookings;

    @Override
    public void run(ApplicationArguments args) {
        try {
            long count = bookings.countByStatusInAndSchedule_ScheduledEndTimeBefore(
                    List.of("BOOKED", "WAITLISTED"), LocalDateTime.now());
            if (count > 0) {
                log.warn("启动自检：有 {} 条预约已结束但未核销，会挡住会员退款；请在「预约与签到」点「一键核销」（或调 POST /api/v1/admin/bookings/overdue/resolve）", count);
            } else {
                log.info("启动自检：没有过期未核销的预约");
            }
        } catch (Exception e) {
            log.warn("启动自检跳过（不影响启动）：{}", e.getMessage());
        }
    }
}
