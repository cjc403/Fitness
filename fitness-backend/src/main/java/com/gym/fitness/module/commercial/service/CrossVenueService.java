package com.gym.fitness.module.commercial.service;

import com.gym.fitness.module.commercial.entity.MemberEntitlementEntity;
import com.gym.fitness.module.commercial.entity.MembershipProductEntity;
import com.gym.fitness.module.commercial.entity.ProductVenueEntity;
import com.gym.fitness.module.commercial.repository.MemberEntitlementRepository;
import com.gym.fitness.module.commercial.repository.ProductVenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 跨门店通用（连锁）规则。
 *
 * <p><b>模型</b>：商品归属一个"销售门店"（收款方，即 {@code membership_product.venue_id}），
 * 并可声明一组"适用门店"（{@code product_venue}）。<b>适用门店为空 = 仅限销售门店</b>，
 * 这样既有商品的行为完全不变（向后兼容）。</p>
 *
 * <p><b>快照</b>：会员开卡时把生效的适用门店写入 {@code member_entitlement.venue_scope}，
 * 之后商品改了适用范围不会追溯影响已售出的卡。</p>
 *
 * <p><b>边界（刻意不做）</b>：跨店退款与价值账分摊仍按"发卡门店"结算——
 * 钱是谁收的就退给谁，避免把资金链路摊到多个门店而失去可追溯性。
 * 跨店升级补差价也不在本轮范围内。</p>
 */
@Service
@RequiredArgsConstructor
public class CrossVenueService {
    private final ProductVenueRepository productVenues;
    private final MemberEntitlementRepository entitlements;

    /** 商品声明的适用门店（不含销售门店本身；空表示仅限销售门店）。 */
    @Transactional(readOnly = true)
    public List<Long> declaredScope(Long productId) {
        return productVenues.findByProductIdOrderByVenueIdAsc(productId).stream()
                .map(ProductVenueEntity::getVenueId).toList();
    }

    /** 覆盖式设置适用门店（去重、剔除销售门店自身）。 */
    @Transactional
    public List<Long> replaceScope(Long productId, Long sellerVenueId, Collection<Long> venueIds) {
        productVenues.deleteByProductId(productId);
        if (venueIds != null) {
            venueIds.stream().filter(Objects::nonNull).distinct()
                    .filter(id -> !id.equals(sellerVenueId))
                    .forEach(id -> {
                        ProductVenueEntity link = new ProductVenueEntity();
                        link.setProductId(productId);
                        link.setVenueId(id);
                        productVenues.save(link);
                    });
        }
        return declaredScope(productId);
    }

    /** 开卡时的快照：销售门店 + 声明的适用门店（销售门店始终可用）。 */
    public String snapshotScope(MembershipProductEntity product) {
        LinkedHashSet<Long> scope = new LinkedHashSet<>();
        scope.add(product.getVenueId());
        scope.addAll(declaredScope(product.getId()));
        return scope.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    /** 某张权益是否可在目标门店使用（发卡门店永远可用）。 */
    public boolean usableAt(String venueScope, Long entitlementVenueId, Long targetVenueId) {
        if (targetVenueId == null) return false;
        if (targetVenueId.equals(entitlementVenueId)) return true;
        return parse(venueScope).contains(targetVenueId);
    }

    /** 会员能否进入/预约某个门店：本人所属门店，或持有覆盖该门店的有效权益。 */
    @Transactional(readOnly = true)
    public boolean memberCanAccessVenue(Long memberId, Long memberVenueId, Long targetVenueId) {
        if (targetVenueId == null) return false;
        if (targetVenueId.equals(memberVenueId)) return true;
        return entitlements.findByMemberIdOrderByIdDesc(memberId).stream()
                .filter(this::currentlyUsable)
                .anyMatch(e -> usableAt(e.getVenueScope(), e.getVenueId(), targetVenueId));
    }

    /** 会员持有、且覆盖目标门店的有效权益（跨店预约时用来挑可用权益）。 */
    @Transactional(readOnly = true)
    public List<MemberEntitlementEntity> coverableEntitlements(Long memberId, Long targetVenueId) {
        if (targetVenueId == null) return List.of();
        return entitlements.findByMemberIdOrderByIdDesc(memberId).stream()
                .filter(this::currentlyUsable)
                .filter(e -> usableAt(e.getVenueScope(), e.getVenueId(), targetVenueId))
                .toList();
    }

    /** 会员可访问的门店集合（门店 id），用于课程可见性。 */
    @Transactional(readOnly = true)
    public Set<Long> accessibleVenues(Long memberId, Long memberVenueId) {
        Set<Long> out = new LinkedHashSet<>();
        if (memberVenueId != null) out.add(memberVenueId);
        for (MemberEntitlementEntity e : entitlements.findByMemberIdOrderByIdDesc(memberId)) {
            if (!currentlyUsable(e)) continue;
            out.addAll(parse(e.getVenueScope()));
            if (e.getVenueId() != null) out.add(e.getVenueId());
        }
        return out;
    }

    private boolean currentlyUsable(MemberEntitlementEntity e) {
        java.time.LocalDate today = com.gym.fitness.module.finance.service.EntitlementValueService.today();
        return "ACTIVE".equals(e.getStatus()) && e.getValidFrom() != null && e.getValidUntil() != null
                && !today.isBefore(e.getValidFrom()) && !today.isAfter(e.getValidUntil())
                && (e.getRemainingUses() == null || e.getRemainingUses() > 0);
    }

    public List<Long> parse(String venueScope) {
        if (venueScope == null || venueScope.isBlank()) return List.of();
        List<Long> out = new ArrayList<>();
        for (String part : venueScope.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                out.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 脏数据不应影响鉴权判断，跳过
            }
        }
        return out;
    }
}
