package com.gym.fitness.module.booking.service;

import com.gym.fitness.module.booking.entity.BookingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 过期预约核销。
 *
 * <p>背景：项目里**没有任何定时任务**，预约一旦建了就停在 {@code BOOKED}，除非有人在前台签到页手工
 * 点「签到」或「爽约」。课结束了却没人处理的历史记录会一直留着，而退款前置条件要求
 * 「没有未完成服务预约」——于是这些陈年记录会**永久挡住会员退款**。</p>
 *
 * <p>这里把这类记录一次核销掉：{@code BOOKED} → 爽约（释放预占、不扣次），
 * {@code WAITLISTED} → 候补失效。范围跟预约列表一致（超管全部门店，其余限本门店）。</p>
 */
@Service
@RequiredArgsConstructor
public class BookingOverdueService {
    private final BookingService bookingService;

    public record OverdueItem(Long id, String memberName, String memberNo, String courseName,
                              String coachName, LocalDateTime startTime, String status) {
    }

    public record OverdueResult(List<Item> noShow, List<Item> cancelled, List<Item> skipped) {
        public record Item(String label, String detail) {
        }
    }

    /** 已结束但仍未核销的预约（供界面预览要处理哪些）。 */
    public List<OverdueItem> overdue() {
        LocalDateTime now = LocalDateTime.now();
        List<OverdueItem> out = new ArrayList<>();
        for (BookingEntity b : bookingService.list()) {
            if (!isOverdue(b, now)) continue;
            var s = b.getSchedule();
            out.add(new OverdueItem(b.getId(),
                    b.getMember() == null ? null : b.getMember().getRealName(),
                    b.getMember() == null ? null : b.getMember().getMemberNo(),
                    s == null || s.getCourse() == null ? null : s.getCourse().getName(),
                    s == null ? null : s.getCoachName(),
                    s == null ? null : s.getScheduledStartTime(),
                    b.getStatus()));
        }
        return out;
    }

    /**
     * 一键核销全部过期预约。
     *
     * <p>刻意不加 {@code @Transactional}：每条都经注入的 {@link BookingService} 走代理，
     * 在自己的事务里执行；某条失败只影响它自己。若外层也开事务，内层异常会把事务标记为
     * rollback-only，整批都会一起失败（这一点与退款侧的一键取消同理）。</p>
     */
    public OverdueResult resolve() {
        List<OverdueResult.Item> noShow = new ArrayList<>(), cancelled = new ArrayList<>(), skipped = new ArrayList<>();
        for (OverdueItem item : overdue()) {
            String label = label(item);
            try {
                if ("BOOKED".equals(item.status())) {
                    bookingService.markNoShow(item.id());
                    noShow.add(new OverdueResult.Item(label, "已记爽约，预占已释放、未扣次数"));
                } else {
                    bookingService.cancel(item.id(), "过期候补自动失效");
                    cancelled.add(new OverdueResult.Item(label, "候补已失效"));
                }
            } catch (RuntimeException ex) {
                skipped.add(new OverdueResult.Item(label, ex.getMessage()));
            }
        }
        return new OverdueResult(noShow, cancelled, skipped);
    }

    private boolean isOverdue(BookingEntity b, LocalDateTime now) {
        var s = b.getSchedule();
        return s != null && s.getScheduledEndTime() != null && s.getScheduledEndTime().isBefore(now)
                && ("BOOKED".equals(b.getStatus()) || "WAITLISTED".equals(b.getStatus()));
    }

    private String label(OverdueItem item) {
        String who = item.memberName() == null ? "会员" : item.memberName();
        String course = item.courseName() == null ? "课程" : "《" + item.courseName() + "》";
        String when = item.startTime() == null ? "" : " " + item.startTime().toString().replace('T', ' ').substring(5, 16);
        return who + " · " + course + when;
    }
}
