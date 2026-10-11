package com.gym.fitness.module.member.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.analytics.service.RiskScoring;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.commercial.repository.MemberEntitlementRepository;
import com.gym.fitness.module.commercial.repository.SalesOrderRepository;
import com.gym.fitness.module.finance.service.Money;
import com.gym.fitness.module.member.dto.AdvisorDtos.*;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.entity.MemberFollowUpEntity;
import com.gym.fitness.module.member.repository.MemberFollowUpRepository;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Read-only contact priorities. Follow-up attempts and paid renewals are separate facts. */
@Service
@RequiredArgsConstructor
public class RetentionService {
    private final MemberRepository members;
    private final MemberFollowUpRepository followUps;
    private final BookingRepository bookings;
    private final MemberEntitlementRepository entitlements;
    private final SalesOrderRepository orders;
    private final SysUserRepository users;
    private final VenueRepository venues;
    private final Clock businessClock = Clock.system(ZoneId.of("Asia/Shanghai"));

    @Transactional(readOnly = true)
    public RetentionWorkbench workbench(String filter, String keyword) {
        String selected = filter == null ? "ALL" : filter.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "TODAY", "OVERDUE", "EXPIRING_7", "EXPIRING_30", "HIGH_RISK").contains(selected)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的回访筛选条件");
        }
        String search = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (search.length() > 100) throw new BusinessException(ErrorCode.PARAM_ERROR, "搜索内容不能超过100字");
        String role = SecurityUtil.getCurrentUserRole();
        if (!Set.of("SALES", "MANAGER", "SUPER_ADMIN").contains(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Long scope = SecurityUtil.isSuperAdmin() ? null : SecurityUtil.getCurrentOrganizationId();
        if (!SecurityUtil.isSuperAdmin() && scope == null) throw new BusinessException(ErrorCode.FORBIDDEN);
        List<MemberEntity> visible = "SALES".equals(role)
            ? members.findBySalesUserIdOrderByIdDesc(SecurityUtil.getCurrentUserId()).stream()
                .filter(m -> Objects.equals(m.getVenueId(), scope)).toList()
            : scope == null ? members.findAll() : members.findByVenueIdOrderByIdDesc(scope);
        LocalDateTime now = LocalDateTime.now(businessClock);
        LocalDate today = now.toLocalDate();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        List<Long> ids = visible.stream().map(MemberEntity::getId).toList();
        Map<Long, MemberFollowUpEntity> latest = new HashMap<>();
        Set<Long> contacted = new HashSet<>();
        if (!ids.isEmpty()) for (var f : followUps.findByMemberIdInOrderByIdDesc(ids)) {
            if (scope != null && !Objects.equals(scope, f.getVenueId())) continue;
            latest.putIfAbsent(f.getMemberId(), f);
            if (f.getCreatedAt() != null && !f.getCreatedAt().isBefore(monthStart) && !f.getCreatedAt().isAfter(now)) contacted.add(f.getMemberId());
        }
        Map<Long, LocalDate> lastVisit = new HashMap<>();
        Map<Long, Long> visits60 = new HashMap<>();
        if (!ids.isEmpty()) for (var b : bookings.findByMemberIdInOrderByBookedAtDesc(ids)) {
            if (!"ATTENDED".equals(b.getStatus()) || b.getSchedule() == null) continue;
            var time = b.getSchedule().getScheduledStartTime();
            if (time == null || time.isAfter(now)) continue;
            LocalDate day = time.toLocalDate();
            lastVisit.merge(b.getMemberId(), day, (a, c) -> c.isAfter(a) ? c : a);
            if (!day.isBefore(today.minusDays(60))) visits60.merge(b.getMemberId(), 1L, Long::sum);
        }
        Set<Long> active = new HashSet<>();
        Map<Long, LocalDate> expiry = new HashMap<>();
        if (!ids.isEmpty()) for (var e : entitlements.findByMemberIdInOrderByIdDesc(ids)) {
            if (scope != null && !Objects.equals(scope, e.getVenueId())) continue;
            if (!"ACTIVE".equals(e.getStatus()) || e.getValidFrom() == null || e.getValidUntil() == null
                || e.getValidFrom().isAfter(today) || e.getValidUntil().isBefore(today)
                || (e.getRemainingUses() != null && e.getRemainingUses() <= 0)) continue;
            active.add(e.getMemberId());
            expiry.merge(e.getMemberId(), e.getValidUntil(), (a, c) -> c.isBefore(a) ? c : a);
        }
        Map<Long, Long> netRenewals = new HashMap<>();
        Map<Long, LocalDateTime> lastRenewal = new HashMap<>();
        if (!ids.isEmpty()) for (var o : orders.findByMemberIdInAndOrderTypeAndPaidAtBetween(ids, "RENEW", monthStart, now)) {
            if (scope != null && !Objects.equals(scope, o.getVenueId())) continue;
            if (!Set.of("PAID", "PARTIALLY_REFUNDED").contains(o.getStatus()) || o.getPaidAmount() == null) continue;
            long net = Money.cents(o.getPaidAmount()) - Money.cents(o.getRefundedAmount() == null ? java.math.BigDecimal.ZERO : o.getRefundedAmount());
            if (net <= 0) continue;
            netRenewals.merge(o.getMemberId(), net, Math::addExact);
            lastRenewal.merge(o.getMemberId(), o.getPaidAt(), (a, c) -> c.isAfter(a) ? c : a);
        }
        Set<Long> advisorIds = new HashSet<>(); Set<Long> venueIds = new HashSet<>();
        for (var m : visible) { if (m.getSalesUserId() != null) advisorIds.add(m.getSalesUserId()); venueIds.add(m.getVenueId()); }
        Map<Long, String> advisorNames = new HashMap<>(); Map<Long, String> venueNames = new HashMap<>();
        users.findAllById(advisorIds).forEach(u -> advisorNames.put(u.getId(), u.getRealName()));
        venues.findAllById(venueIds).forEach(v -> venueNames.put(v.getId(), v.getName()));
        List<RetentionMember> rows = new ArrayList<>();
        for (var m : visible) {
            var last = latest.get(m.getId());
            var next = last == null ? null : last.getNextFollowAt();
            String state = next == null ? "UNSCHEDULED" : next.toLocalDate().isBefore(today) ? "OVERDUE"
                : next.toLocalDate().equals(today) ? "TODAY" : "UPCOMING";
            var risk = RiskScoring.score(lastVisit.get(m.getId()), m.getCreatedAt() == null ? null : m.getCreatedAt().toLocalDate(),
                visits60.getOrDefault(m.getId(), 0L), active.contains(m.getId()), today);
            LocalDate expires = expiry.get(m.getId());
            rows.add(new RetentionMember(m.getId(), m.getMemberNo(), m.getRealName(), m.getPhone(), m.getStatus(),
                m.getVenueId(), venueNames.getOrDefault(m.getVenueId(), "未命名门店"), m.getSalesUserId(),
                m.getSalesUserId() == null ? "未分配顾问" : advisorNames.getOrDefault(m.getSalesUserId(), "原归属顾问"),
                last == null ? null : last.getCreatedAt(), last == null ? null : last.getContent(), last == null ? null : last.getOutcome(),
                next, state, risk.value(), risk.level(), risk.reason(), expires,
                expires == null ? null : ChronoUnit.DAYS.between(today, expires), netRenewals.containsKey(m.getId()),
                lastRenewal.get(m.getId()), Money.yuan(netRenewals.getOrDefault(m.getId(), 0L))));
        }
        rows.sort(Comparator.comparingInt((RetentionMember r) -> priority(r.followStatus()))
            .thenComparing(RetentionMember::nextFollowAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(RetentionMember::riskScore, Comparator.reverseOrder()).thenComparing(RetentionMember::memberId));
        RetentionSummary summary = summary(rows, contacted);
        List<RetentionAdvisor> groups = new ArrayList<>();
        if (!"SALES".equals(role)) {
            Map<String, List<RetentionMember>> grouped = new LinkedHashMap<>();
            for (var r : rows) grouped.computeIfAbsent(String.valueOf(r.salesUserId()), k -> new ArrayList<>()).add(r);
            for (var group : grouped.values()) {
                var s = summary(group, contacted); var first = group.get(0);
                groups.add(new RetentionAdvisor(first.salesUserId(), first.advisorName(), s.totalMembers(), s.todayFollowUps(),
                    s.overdueFollowUps(), s.followedThisMonth(), s.renewedThisMonth()));
            }
        }
        var filtered = rows.stream().filter(r -> matches(r, selected))
            .filter(r -> search.isEmpty() || (r.realName() + " " + r.memberNo() + " " + r.phone() + " " + r.advisorName()).toLowerCase(Locale.ROOT).contains(search)).toList();
        return new RetentionWorkbench(now, summary, filtered, groups);
    }

    private RetentionSummary summary(List<RetentionMember> rows, Set<Long> contacted) {
        return new RetentionSummary(rows.size(), rows.stream().filter(r -> "TODAY".equals(r.followStatus())).count(),
            rows.stream().filter(r -> "OVERDUE".equals(r.followStatus())).count(),
            rows.stream().filter(r -> expiring(r, 7)).count(), rows.stream().filter(r -> expiring(r, 30)).count(),
            rows.stream().filter(r -> "HIGH".equals(r.riskLevel())).count(),
            rows.stream().filter(r -> contacted.contains(r.memberId())).count(),
            rows.stream().filter(RetentionMember::renewedThisMonth).count(),
            rows.stream().map(RetentionMember::renewalNetAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
    }
    private boolean matches(RetentionMember r, String filter) {
        return switch (filter) {
            case "TODAY", "OVERDUE" -> filter.equals(r.followStatus());
            case "EXPIRING_7" -> expiring(r, 7);
            case "EXPIRING_30" -> expiring(r, 30);
            case "HIGH_RISK" -> "HIGH".equals(r.riskLevel());
            default -> true;
        };
    }
    private boolean expiring(RetentionMember r, int days) { return r.expiryDays() != null && r.expiryDays() >= 0 && r.expiryDays() <= days; }
    private int priority(String state) { return switch (state) { case "OVERDUE" -> 0; case "TODAY" -> 1; case "UPCOMING" -> 2; default -> 3; }; }
}
