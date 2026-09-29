package com.gym.fitness.module.member.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.commercial.entity.SalesOrderEntity;
import com.gym.fitness.module.commercial.repository.SalesOrderRepository;
import com.gym.fitness.module.member.dto.AdvisorDtos.*;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.entity.MemberFollowUpEntity;
import com.gym.fitness.module.member.repository.MemberFollowUpRepository;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.finance.service.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 会籍顾问 / 专属教练的归属视图与跟进记录。
 *
 * <p>把 {@code member.sales_user_id}、{@code member.coach_id} 这两个既有字段真正用起来：
 * 会员归属到人之后，顾问才有"我的会员、我的跟进、我的业绩"。</p>
 */
@Service
@RequiredArgsConstructor
public class AdvisorService {
    private final MemberRepository members;
    private final MemberFollowUpRepository followUps;
    private final SysUserRepository users;
    private final SalesOrderRepository orders;
    private final com.gym.fitness.module.booking.repository.BookingRepository bookings;
    private final com.gym.fitness.module.commercial.repository.MemberEntitlementRepository entitlements;

    // ---------------------------------------------------------------- 归属

    /** 可选为归属人的员工（会籍顾问或教练），供会员档案下拉使用。 */
    @Transactional(readOnly = true)
    public List<AdvisorView> listAssignable(String roleCode) {
        String role = roleCode == null ? "SALES" : roleCode.trim().toUpperCase();
        if (!Set.of("SALES", "COACH").contains(role)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只能查询会籍顾问或教练");
        }
        return users.findAll().stream()
                .filter(u -> "ACTIVE".equals(u.getStatus()) && role.equals(u.getRole().getCode()))
                .filter(u -> SecurityUtil.isSuperAdmin() || Objects.equals(u.getVenueId(), currentVenue()))
                .sorted(Comparator.comparing(SysUserEntity::getRealName, Comparator.nullsLast(String::compareTo)))
                .map(u -> new AdvisorView(u.getId(), u.getRealName(), u.getUsername(), u.getVenueId()))
                .toList();
    }

    // ---------------------------------------------------------------- 跟进

    @Transactional(readOnly = true)
    public List<FollowUpView> memberFollowUps(Long memberId) {
        requireMember(memberId);
        return followUps.findByMemberIdOrderByIdDesc(memberId).stream().map(this::toView).toList();
    }

    /** 记录一次跟进；只有归属本人、店长或总部可以为该会员记跟进。 */
    @Transactional
    public FollowUpView addFollowUp(Long memberId, FollowUpRequest request) {
        MemberEntity member = requireMember(memberId);
        assertCanFollow(member);
        String type = request.getFollowType().trim().toUpperCase();
        if (!Set.of("CALL", "WECHAT", "VISIT", "OTHER").contains(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "跟进方式只能是电话、微信、到店或其它");
        }
        MemberFollowUpEntity entity = new MemberFollowUpEntity();
        entity.setMemberId(member.getId());
        entity.setStaffUserId(SecurityUtil.getCurrentUserId());
        entity.setVenueId(member.getVenueId());
        entity.setFollowType(type);
        entity.setContent(request.getContent().trim());
        entity.setNextFollowAt(request.getNextFollowAt());
        return toView(followUps.save(entity));
    }

    // ---------------------------------------------------------------- 工作台

    /** 当前登录顾问（或教练）的工作台：我的会员 + 我的跟进 + 我的开卡业绩。 */
    @Transactional(readOnly = true)
    public AdvisorWorkbench workbench() {
        Long me = SecurityUtil.getCurrentUserId();
        SysUserEntity user = users.findById(me)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<MemberEntity> mine = members.findBySalesUserIdOrderByIdDesc(me);
        if (mine.isEmpty()) {
            // 教练登录时看归属自己的学员
            mine = members.findByCoachIdOrderByIdDesc(me);
        }
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<Long> myIds = mine.stream().map(MemberEntity::getId).toList();
        Map<Long, MemberFollowUpEntity> latest = new HashMap<>();
        Map<Long, Long> followCount = new HashMap<>();
        List<MemberFollowUpEntity> all = myIds.isEmpty() ? List.of()
                : followUps.findByMemberIdInOrderByIdDesc(myIds);
        for (MemberFollowUpEntity f : all) {
            latest.putIfAbsent(f.getMemberId(), f);
            followCount.merge(f.getMemberId(), 1L, Long::sum);
        }

        // 流失风险分：与经营分析同一口径（规则化，可解释）
        java.util.Map<Long, java.time.LocalDate> lastVisit = new java.util.HashMap<>();
        java.util.Map<Long, Long> visits60 = new java.util.HashMap<>();
        java.time.LocalDate today0 = LocalDate.now();
        if (!myIds.isEmpty()) {
            for (var b : bookings.findAllByOrderByBookedAtDesc()) {
                if (!myIds.contains(b.getMemberId()) || !"ATTENDED".equals(b.getStatus()) || b.getSchedule() == null) continue;
                var at = b.getSchedule().getScheduledStartTime();
                if (at == null) continue;
                lastVisit.merge(b.getMemberId(), at.toLocalDate(), (a, c) -> c.isAfter(a) ? c : a);
                if (!at.toLocalDate().isBefore(today0.minusDays(60))) visits60.merge(b.getMemberId(), 1L, Long::sum);
            }
        }
        java.util.Set<Long> activeEnt = new java.util.HashSet<>();
        for (var e : entitlements.findAllByOrderByIdDesc()) {
            if (myIds.contains(e.getMemberId()) && "ACTIVE".equals(e.getStatus())) activeEnt.add(e.getMemberId());
        }

        List<AdvisorMember> rows = new ArrayList<>();
        long due = 0;
        for (MemberEntity m : mine) {
            MemberFollowUpEntity last = latest.get(m.getId());
            LocalDateTime next = last == null ? null : last.getNextFollowAt();
            boolean isDue = next != null && !next.isAfter(now);
            if (isDue) due++;
            var risk = com.gym.fitness.module.analytics.service.RiskScoring.score(
                    lastVisit.get(m.getId()), m.getCreatedAt() == null ? null : m.getCreatedAt().toLocalDate(),
                    visits60.getOrDefault(m.getId(), 0L), activeEnt.contains(m.getId()), today0);
            rows.add(new AdvisorMember(m.getId(), m.getMemberNo(), m.getRealName(), m.getPhone(), m.getStatus(),
                    last == null ? null : last.getCreatedAt(),
                    last == null ? null : last.getContent(),
                    next, isDue, risk.value(), risk.level()));
        }
        rows.sort(Comparator.comparing(AdvisorMember::due).reversed()
                .thenComparing(AdvisorMember::riskScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AdvisorMember::lastFollowAt, Comparator.nullsFirst(Comparator.naturalOrder())));

        long newThisMonth = mine.stream()
                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isAfter(monthStart)).count();
        long followThisWeek = all.stream()
                .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isAfter(weekStart)
                        && Objects.equals(f.getStaffUserId(), me)).count();
        List<SalesOrderEntity> myOrders = orders.findByOperatorIdOrderByIdDesc(me);
        long ordersThisMonth = myOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(monthStart)).count();
        BigDecimal amountThisMonth = myOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(monthStart))
                .map(SalesOrderEntity::getPaidAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AdvisorSummary summary = new AdvisorSummary(mine.size(), newThisMonth, followThisWeek, due,
                ordersThisMonth, Money.yuan(Money.cents(amountThisMonth)));
        return new AdvisorWorkbench(user.getRealName(), summary, rows);
    }

    // ---------------------------------------------------------------- 内部

    private MemberEntity requireMember(Long memberId) {
        MemberEntity member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!SecurityUtil.isSuperAdmin() && !Objects.equals(member.getVenueId(), currentVenue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能操作其他门店的会员");
        }
        return member;
    }

    /** 归属本人、店长或总部可以为该会员记跟进；其他顾问不能碰别人的会员。 */
    private void assertCanFollow(MemberEntity member) {
        Long me = SecurityUtil.getCurrentUserId();
        boolean owner = Objects.equals(member.getSalesUserId(), me) || Objects.equals(member.getCoachId(), me);
        boolean manager = SecurityUtil.hasRole("MANAGER") || SecurityUtil.isSuperAdmin();
        if (!owner && !manager) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能跟进归属自己的会员，或由店长/总部代记");
        }
    }

    private FollowUpView toView(MemberFollowUpEntity e) {
        String memberName = members.findById(e.getMemberId()).map(MemberEntity::getRealName).orElse(null);
        String staffName = users.findById(e.getStaffUserId()).map(SysUserEntity::getRealName).orElse(null);
        return new FollowUpView(e.getId(), e.getMemberId(), memberName, e.getStaffUserId(), staffName,
                e.getFollowType(), e.getContent(), e.getNextFollowAt(), e.getCreatedAt());
    }

    private Long currentVenue() {
        return SecurityUtil.getCurrentOrganizationId();
    }
}
