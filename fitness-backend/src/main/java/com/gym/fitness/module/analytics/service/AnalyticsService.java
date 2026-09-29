package com.gym.fitness.module.analytics.service;

import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.analytics.dto.AnalyticsDtos.*;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.commercial.entity.MemberEntitlementEntity;
import com.gym.fitness.module.commercial.entity.SalesOrderEntity;
import com.gym.fitness.module.commercial.repository.MemberEntitlementRepository;
import com.gym.fitness.module.commercial.repository.SalesOrderRepository;
import com.gym.fitness.module.finance.service.Money;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.review.entity.CourseReviewEntity;
import com.gym.fitness.module.review.repository.CourseReviewRepository;
import com.gym.fitness.module.training.entity.PrivateLessonEntity;
import com.gym.fitness.module.training.repository.PrivateLessonRepository;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 经营分析：教练绩效、会员消费与到店、会员流失风险。
 *
 * <p>数据量口径：本项目规模（几千行）下直接在内存聚合即可，避免写一堆只为报表存在的 SQL；
 * 如果将来数据量上来，再把这些聚合下沉到数据库。</p>
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private static final int SILENT_DAYS = 30;
    private static final int CHURN_DAYS = 60;

    private final MemberRepository members;
    private final BookingRepository bookings;
    private final SalesOrderRepository orders;
    private final MemberEntitlementRepository entitlements;
    private final PrivateLessonRepository lessons;
    private final CourseReviewRepository reviews;
    private final SysUserRepository users;

    // ---------------------------------------------------------------- 概览

    @Transactional(readOnly = true)
    public Overview overview(LocalDate from, LocalDate to) {
        // 注意：这两个变量会被 lambda 捕获，必须保持 effectively final（不能改写）
        LocalDate start = from == null ? LocalDate.now().minusDays(29) : from;
        LocalDate requestedEnd = to == null ? LocalDate.now() : to;
        LocalDate end = requestedEnd.isBefore(start) ? start : requestedEnd;

        List<MemberEntity> scoped = scopedMembers();
        List<BookingEntity> scopedBookings = scopedBookings(scoped);
        Map<Long, LocalDate> lastVisit = lastVisitMap(scopedBookings, null);
        Map<Long, Long> visits60 = visitsSince(scopedBookings, LocalDate.now().minusDays(60));
        Set<Long> withEntitlement = membersWithActiveEntitlement(scoped);

        long newMembers = scoped.stream()
                .filter(m -> m.getCreatedAt() != null
                        && !m.getCreatedAt().toLocalDate().isBefore(start)
                        && !m.getCreatedAt().toLocalDate().isAfter(end)).count();
        long active = 0, silent = 0, churned = 0, highRisk = 0;
        for (MemberEntity m : scoped) {
            long days = daysSince(lastVisit.get(m.getId()), m, LocalDate.now());
            if (days <= SILENT_DAYS) active++;
            else if (days <= CHURN_DAYS) silent++;
            else churned++;
            var score = RiskScoring.score(lastVisit.get(m.getId()), joinedAt(m),
                    visits60.getOrDefault(m.getId(), 0L), withEntitlement.contains(m.getId()), LocalDate.now());
            if ("HIGH".equals(score.level())) highRisk++;
        }

        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay();
        long attendances = scopedBookings.stream()
                .filter(b -> "ATTENDED".equals(b.getStatus()) && inRange(b.getSchedule() == null ? null
                        : b.getSchedule().getScheduledStartTime(), rangeStart, rangeEnd)).count();
        BigDecimal revenue = scopedOrders(scoped).stream()
                .filter(o -> inRange(o.getCreatedAt(), rangeStart, rangeEnd))
                .filter(o -> o.getPaidAmount() != null)
                .map(SalesOrderEntity::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new Overview(start, end, newMembers, scoped.size(), active, silent, churned,
                attendances, Money.yuan(Money.cents(revenue)), highRisk);
    }

    // ---------------------------------------------------------------- 教练绩效

    @Transactional(readOnly = true)
    public List<CoachRank> coachRanking(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().minusDays(29) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay();
        List<MemberEntity> scoped = scopedMembers();
        List<BookingEntity> scopedBookings = scopedBookings(scoped);

        Map<Long, Long> privateCount = new LinkedHashMap<>();
        Map<Long, BigDecimal> privateRevenue = new LinkedHashMap<>();
        for (PrivateLessonEntity l : lessons.findAll()) {
            if (l.getCoachId() == null || !"CONFIRMED".equals(l.getStatus())) continue;
            if (!inRange(l.getConfirmedAt(), rangeStart, rangeEnd)) continue;
            privateCount.merge(l.getCoachId(), 1L, Long::sum);
            privateRevenue.merge(l.getCoachId(), l.getUnitPrice() == null ? BigDecimal.ZERO : l.getUnitPrice(),
                    BigDecimal::add);
        }
        Map<Long, Long> groupCount = new LinkedHashMap<>();
        for (BookingEntity b : scopedBookings) {
            if (!"ATTENDED".equals(b.getStatus()) || b.getSchedule() == null) continue;
            if (!inRange(b.getSchedule().getScheduledStartTime(), rangeStart, rangeEnd)) continue;
            Long coachId = b.getSchedule().getCoachId();
            if (coachId != null) groupCount.merge(coachId, 1L, Long::sum);
        }
        Map<Long, List<CourseReviewEntity>> reviewsByCoach = reviews.findAll().stream()
                .filter(r -> r.getCoachId() != null)
                .collect(Collectors.groupingBy(CourseReviewEntity::getCoachId));

        Set<Long> coachIds = new TreeSet<>();
        coachIds.addAll(privateCount.keySet());
        coachIds.addAll(groupCount.keySet());
        coachIds.addAll(reviewsByCoach.keySet());
        List<CoachRank> out = new ArrayList<>();
        for (Long coachId : coachIds) {
            List<CourseReviewEntity> rs = reviewsByCoach.getOrDefault(coachId, List.of());
            BigDecimal avg = rs.isEmpty() ? null : BigDecimal.valueOf(rs.stream()
                    .mapToInt(CourseReviewEntity::getRating).average().orElse(0))
                    .setScale(2, RoundingMode.HALF_UP);
            String name = users.findById(coachId).map(u -> u.getRealName()).orElse("教练 " + coachId);
            out.add(new CoachRank(coachId, name, privateCount.getOrDefault(coachId, 0L),
                    Money.yuan(Money.cents(privateRevenue.getOrDefault(coachId, BigDecimal.ZERO))),
                    groupCount.getOrDefault(coachId, 0L), rs.size(), avg));
        }
        out.sort(Comparator.comparing(CoachRank::privateLessons).reversed()
                .thenComparing(CoachRank::groupAttendances).reversed());
        return out;
    }

    // ---------------------------------------------------------------- 会员风险

    @Transactional(readOnly = true)
    public MemberAnalytics memberAnalytics(boolean riskOnly) {
        LocalDate today = LocalDate.now();
        List<MemberEntity> scoped = scopedMembers();
        List<BookingEntity> scopedBookings = scopedBookings(scoped);
        Map<Long, LocalDate> lastVisit = lastVisitMap(scopedBookings, null);
        Map<Long, Long> visits60 = visitsSince(scopedBookings, today.minusDays(60));
        Set<Long> withEntitlement = membersWithActiveEntitlement(scoped);
        Map<Long, BigDecimal> spending = new HashMap<>();
        for (SalesOrderEntity o : scopedOrders(scoped)) {
            if (o.getPaidAmount() == null) continue;
            spending.merge(o.getMemberId(), o.getPaidAmount(), BigDecimal::add);
        }
        Map<Long, String> advisorNames = new HashMap<>();

        List<MemberRisk> rows = new ArrayList<>();
        long high = 0, medium = 0;
        for (MemberEntity m : scoped) {
            var score = RiskScoring.score(lastVisit.get(m.getId()), joinedAt(m),
                    visits60.getOrDefault(m.getId(), 0L), withEntitlement.contains(m.getId()), today);
            if ("HIGH".equals(score.level())) high++;
            else if ("MEDIUM".equals(score.level())) medium++;
            if (riskOnly && !"HIGH".equals(score.level())) continue;
            String advisorName = null;
            if (m.getSalesUserId() != null) {
                advisorName = advisorNames.computeIfAbsent(m.getSalesUserId(),
                        id -> users.findById(id).map(u -> u.getRealName()).orElse(null));
            }
            rows.add(new MemberRisk(m.getId(), m.getMemberNo(), m.getRealName(), m.getPhone(),
                    m.getSalesUserId(), advisorName, lastVisit.get(m.getId()),
                    visits60.getOrDefault(m.getId(), 0L),
                    Money.yuan(Money.cents(spending.getOrDefault(m.getId(), BigDecimal.ZERO))),
                    score.value(), score.level(), score.reason()));
        }
        rows.sort(Comparator.comparing(MemberRisk::riskScore).reversed());
        return new MemberAnalytics(rows, high, medium);
    }

    // ---------------------------------------------------------------- 内部

    private List<MemberEntity> scopedMembers() {
        return SecurityUtil.isSuperAdmin() ? members.findAll() : members.findByVenueIdOrderByIdDesc(requireVenue());
    }

    private List<BookingEntity> scopedBookings(List<MemberEntity> scoped) {
        Set<Long> ids = scoped.stream().map(MemberEntity::getId).collect(Collectors.toSet());
        return bookings.findAllByOrderByBookedAtDesc().stream()
                .filter(b -> ids.contains(b.getMemberId())).toList();
    }

    private List<SalesOrderEntity> scopedOrders(List<MemberEntity> scoped) {
        Set<Long> ids = scoped.stream().map(MemberEntity::getId).collect(Collectors.toSet());
        return orders.findAll().stream().filter(o -> ids.contains(o.getMemberId())).toList();
    }

    private Set<Long> membersWithActiveEntitlement(List<MemberEntity> scoped) {
        Set<Long> ids = scoped.stream().map(MemberEntity::getId).collect(Collectors.toSet());
        return entitlements.findAllByOrderByIdDesc().stream()
                .filter(e -> ids.contains(e.getMemberId()) && "ACTIVE".equals(e.getStatus()))
                .map(MemberEntitlementEntity::getMemberId).collect(Collectors.toSet());
    }

    private Map<Long, LocalDate> lastVisitMap(List<BookingEntity> scopedBookings, LocalDate unused) {
        Map<Long, LocalDate> out = new HashMap<>();
        for (BookingEntity b : scopedBookings) {
            if (!"ATTENDED".equals(b.getStatus()) || b.getSchedule() == null) continue;
            LocalDateTime at = b.getSchedule().getScheduledStartTime();
            if (at == null) continue;
            LocalDate day = at.toLocalDate();
            out.merge(b.getMemberId(), day, (a, c) -> c.isAfter(a) ? c : a);
        }
        return out;
    }

    private Map<Long, Long> visitsSince(List<BookingEntity> scopedBookings, LocalDate since) {
        Map<Long, Long> out = new HashMap<>();
        for (BookingEntity b : scopedBookings) {
            if (!"ATTENDED".equals(b.getStatus()) || b.getSchedule() == null) continue;
            LocalDateTime at = b.getSchedule().getScheduledStartTime();
            if (at == null || at.toLocalDate().isBefore(since)) continue;
            out.merge(b.getMemberId(), 1L, Long::sum);
        }
        return out;
    }

    private LocalDate joinedAt(MemberEntity m) {
        return m.getCreatedAt() == null ? null : m.getCreatedAt().toLocalDate();
    }

    private long daysSince(LocalDate lastVisit, MemberEntity m, LocalDate today) {
        LocalDate reference = lastVisit != null ? lastVisit : joinedAt(m);
        if (reference == null) return 999;
        return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(reference, today));
    }

    private boolean inRange(LocalDateTime at, LocalDateTime start, LocalDateTime end) {
        return at != null && !at.isBefore(start) && at.isBefore(end);
    }

    private Long requireVenue() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) throw new com.gym.fitness.common.exception.BusinessException(
                com.gym.fitness.common.constant.ErrorCode.FORBIDDEN, "缺少门店上下文");
        return venueId;
    }
}
