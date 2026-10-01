package com.gym.fitness.module.commercial.service;

import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.commercial.entity.CrossVenueServiceEntity;
import com.gym.fitness.module.commercial.entity.MemberEntitlementEntity;
import com.gym.fitness.module.commercial.repository.CrossVenueServiceRepository;
import com.gym.fitness.module.training.entity.PrivateLessonEntity;
import com.gym.fitness.module.organization.entity.VenueEntity;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 跨店服务分摊。
 *
 * <p><b>为什么需要</b>：跨店通用卡由 A 店收款，会员可能在 B 店上课——A 拿了钱、B 出了服务。
 * 这里把"B 店提供了多少服务、对应多少钱"记下来，总部据此在门店之间结算。</p>
 *
 * <p><b>边界（刻意不做）</b>：只记账、不自动打款（真实资金划转要接真实支付通道）；
 * <b>退款与价值账仍按发卡门店</b>，本表不参与任何金额计算，只用于对账与内部结算参考。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossVenueSettlementService {
    private static final String REF_LESSON = "LESSON";

    private final CrossVenueServiceRepository services;
    private final VenueRepository venues;

    /** 私教消课时登记一笔：发卡门店取自权益，服务门店取自排课。同店不记。 */
    @Transactional
    public void recordLesson(PrivateLessonEntity lesson, MemberEntitlementEntity entitlement, BigDecimal amount) {
        if (lesson == null || entitlement == null || amount == null || lesson.getVenueId() == null) return;
        Long issuer = entitlement.getVenueId();
        Long serving = lesson.getVenueId();
        if (issuer == null || Objects.equals(issuer, serving)) return;
        if (services.findByRefTypeAndRefId(REF_LESSON, lesson.getId()).isPresent()) return;

        CrossVenueServiceEntity entity = new CrossVenueServiceEntity();
        entity.setEntitlementId(entitlement.getId());
        entity.setMemberId(lesson.getMemberId());
        entity.setIssuerVenueId(issuer);
        entity.setServingVenueId(serving);
        entity.setRefType(REF_LESSON);
        entity.setRefId(lesson.getId());
        entity.setAmount(amount);
        entity.setNote("私教消课跨店分摊");
        services.save(entity);
        log.info("跨店分摊：课程 {} 在门店 {} 消课，发卡门店 {}，金额 {}", lesson.getId(), serving, issuer, amount);
    }

    /** 冲正消课时撤销分摊记录（服务没发生，就不该结算）。 */
    @Transactional
    public void voidLesson(Long lessonId) {
        if (lessonId == null) return;
        services.findByRefTypeAndRefId(REF_LESSON, lessonId).ifPresent(services::delete);
    }

    // ---------------------------------------------------------------- 汇总

    @Data
    public static class Row {
        private Long issuerVenueId;
        private String issuerVenueName;
        private Long servingVenueId;
        private String servingVenueName;
        private long count;
        private BigDecimal amount;
    }

    @Data
    public static class Summary {
        private LocalDate from;
        private LocalDate to;
        private long totalCount;
        private BigDecimal totalAmount;
        /** 各门店作为"服务方"应分入的合计。 */
        private Map<String, BigDecimal> servingTotals = new LinkedHashMap<>();
        private List<Row> rows = new ArrayList<>();
    }

    /** 按（发卡门店 → 服务门店）汇总；店长只看与本店有关的分摊。 */
    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<CrossVenueServiceEntity> all = services.findByOccurredAtBetweenOrderByIdAsc(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        Map<Long, String> names = venues.findAll().stream()
                .collect(Collectors.toMap(VenueEntity::getId, VenueEntity::getName, (a, b) -> a));

        boolean superAdmin = SecurityUtil.isSuperAdmin();
        Long myVenue = superAdmin ? null : SecurityUtil.getCurrentOrganizationId();

        Summary out = new Summary();
        out.setFrom(start);
        out.setTo(end);
        out.setTotalAmount(BigDecimal.ZERO);
        Map<String, Row> grouped = new LinkedHashMap<>();
        Map<String, BigDecimal> servingTotals = new LinkedHashMap<>();
        for (CrossVenueServiceEntity s : all) {
            if (!superAdmin && myVenue != null
                    && !myVenue.equals(s.getServingVenueId()) && !myVenue.equals(s.getIssuerVenueId())) continue;
            String key = s.getIssuerVenueId() + "-" + s.getServingVenueId();
            Row row = grouped.computeIfAbsent(key, k -> {
                Row r = new Row();
                r.setIssuerVenueId(s.getIssuerVenueId());
                r.setIssuerVenueName(names.getOrDefault(s.getIssuerVenueId(), "门店 " + s.getIssuerVenueId()));
                r.setServingVenueId(s.getServingVenueId());
                r.setServingVenueName(names.getOrDefault(s.getServingVenueId(), "门店 " + s.getServingVenueId()));
                r.setAmount(BigDecimal.ZERO);
                return r;
            });
            row.setCount(row.getCount() + 1);
            row.setAmount(row.getAmount().add(s.getAmount()));
            out.setTotalCount(out.getTotalCount() + 1);
            out.setTotalAmount(out.getTotalAmount().add(s.getAmount()));
            servingTotals.merge(names.getOrDefault(s.getServingVenueId(), "门店 " + s.getServingVenueId()),
                    s.getAmount(), BigDecimal::add);
        }
        out.setRows(new ArrayList<>(grouped.values()));
        out.setServingTotals(servingTotals);
        return out;
    }
}
