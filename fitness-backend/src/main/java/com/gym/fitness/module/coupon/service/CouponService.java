package com.gym.fitness.module.coupon.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.commercial.entity.MembershipProductEntity;
import com.gym.fitness.module.commercial.repository.MembershipProductRepository;
import com.gym.fitness.module.coupon.dto.CouponDtos.*;
import com.gym.fitness.module.coupon.entity.CouponTemplateEntity;
import com.gym.fitness.module.coupon.entity.MemberCouponEntity;
import com.gym.fitness.module.coupon.repository.CouponTemplateRepository;
import com.gym.fitness.module.coupon.repository.MemberCouponRepository;
import com.gym.fitness.module.finance.service.Money;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 优惠券：模板管理、发放、可用性校验与抵扣计算。
 *
 * <p>资金纪律：抵扣金额与应付金额一律**在服务端按分计算**，不接受前端传金额；
 * 折扣后的实收金额写进订单的 paidAmount，权益价值账按实收入账，
 * 因此退款自然按实付计算，不会按标价多退。</p>
 */
@Service
@RequiredArgsConstructor
public class CouponService {
    public static final String TYPE_FULL_REDUCTION = "FULL_REDUCTION";
    public static final String TYPE_DISCOUNT = "DISCOUNT";

    private final CouponTemplateRepository templates;
    private final MemberCouponRepository memberCoupons;
    private final MembershipProductRepository products;
    private final MemberRepository members;
    private final com.gym.fitness.module.commercial.service.CrossVenueService crossVenue;

    // ---------------------------------------------------------------- 模板

    @Transactional(readOnly = true)
    public List<TemplateView> listTemplates() {
        List<CouponTemplateEntity> list = SecurityUtil.isSuperAdmin()
                ? templates.findAllByOrderByIdDesc()
                : templates.findByVenueIdOrderByIdDesc(requireVenue());
        return list.stream().map(this::toTemplateView).toList();
    }

    @Transactional
    public TemplateView createTemplate(TemplateRequest request) {
        CouponTemplateEntity entity = new CouponTemplateEntity();
        entity.setVenueId(resolveVenueId(request.getVenueId()));
        applyTemplate(entity, request);
        entity.setIssuedQuantity(0);
        entity.setCreatedBy(SecurityUtil.getCurrentUserId());
        return toTemplateView(templates.save(entity));
    }

    @Transactional
    public TemplateView updateTemplate(Long id, TemplateRequest request) {
        CouponTemplateEntity entity = templates.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "优惠券模板不存在"));
        assertVenue(entity.getVenueId());
        // 已发出的券按各自快照生效，这里只改模板本身与后续发放
        entity.setVenueId(resolveVenueId(request.getVenueId() == null ? entity.getVenueId() : request.getVenueId()));
        applyTemplate(entity, request);
        return toTemplateView(templates.save(entity));
    }

    private void applyTemplate(CouponTemplateEntity entity, TemplateRequest request) {
        String type = request.getCouponType().trim().toUpperCase();
        if (!TYPE_FULL_REDUCTION.equals(type) && !TYPE_DISCOUNT.equals(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "优惠券类型只能是满减或折扣");
        }
        if (request.getValidUntil().isBefore(request.getValidFrom())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "有效期结束日期不能早于开始日期");
        }
        long threshold = request.getThresholdCents() == null ? 0L : request.getThresholdCents();
        long discount = request.getDiscountCents() == null ? 0L : request.getDiscountCents();
        BigDecimal rate = request.getDiscountRate();
        if (TYPE_FULL_REDUCTION.equals(type)) {
            if (discount <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR, "满减券必须填写减免金额");
            if (threshold > 0 && discount > threshold) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "减免金额不能高于使用门槛");
            }
            rate = null;
        } else {
            if (rate == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "折扣券必须填写折扣率");
            if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "折扣率必须在 0 与 1 之间（如 0.9 表示九折）");
            }
            discount = 0L;
        }
        entity.setName(request.getName().trim());
        entity.setCouponType(type);
        entity.setThresholdCents(threshold);
        entity.setDiscountCents(discount);
        entity.setDiscountRate(rate);
        entity.setTotalQuantity(request.getTotalQuantity() == null ? 0 : request.getTotalQuantity());
        entity.setValidFrom(request.getValidFrom());
        entity.setValidUntil(request.getValidUntil());
        entity.setStatus(request.getStatus() == null ? "ACTIVE" : request.getStatus().trim().toUpperCase());
        entity.setNote(request.getNote());
    }

    // ---------------------------------------------------------------- 发放

    @Transactional
    public int issue(Long templateId, IssueRequest request) {
        CouponTemplateEntity template = templates.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "优惠券模板不存在"));
        assertVenue(template.getVenueId());
        if (!"ACTIVE".equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该优惠券已停用，不能发放");
        }
        if (template.getValidUntil().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该优惠券已过期，不能发放");
        }
        int perMember = request.getQuantityPerMember() == null ? 1 : request.getQuantityPerMember();
        List<MemberEntity> targets = new ArrayList<>();
        for (Long memberId : request.getMemberIds()) {
            MemberEntity member = members.findById(memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            assertVenue(member.getVenueId());
            targets.add(member);
        }
        int want = targets.size() * perMember;
        if (template.getTotalQuantity() > 0
                && template.getIssuedQuantity() + want > template.getTotalQuantity()) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "发放数量超出剩余额度（剩余 " + Math.max(0, template.getTotalQuantity() - template.getIssuedQuantity()) + " 张）");
        }
        LocalDateTime now = LocalDateTime.now();
        int issued = 0;
        for (MemberEntity member : targets) {
            for (int i = 0; i < perMember; i++) {
                MemberCouponEntity coupon = new MemberCouponEntity();
                coupon.setCouponTemplateId(template.getId());
                coupon.setMemberId(member.getId());
                coupon.setVenueId(member.getVenueId());
                coupon.setCode(UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
                coupon.setStatus("UNUSED");
                coupon.setIssuedAt(now);
                coupon.setDiscountCents(0L);
                coupon.setValidFrom(template.getValidFrom());
                coupon.setValidUntil(template.getValidUntil());
                memberCoupons.save(coupon);
                issued++;
            }
        }
        template.setIssuedQuantity(template.getIssuedQuantity() + issued);
        templates.save(template);
        return issued;
    }

    // ---------------------------------------------------------------- 查询

    @Transactional(readOnly = true)
    public List<MemberCouponView> memberCoupons(Long memberId) {
        MemberEntity member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        assertVenue(member.getVenueId());
        return memberCoupons.findByMemberIdOrderByIdDesc(memberId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<MemberCouponView> myCoupons() {
        MemberEntity member = currentMember();
        return memberCoupons.findByMemberIdOrderByIdDesc(member.getId()).stream().map(this::toView).toList();
    }

    /** 会员买某商品时的可用券试算（只算不落库），供界面显示可用券与折后金额。 */
    @Transactional(readOnly = true)
    public CouponQuote quoteForCurrentMember(Long productId) {
        return quote(currentMember().getId(), productId);
    }

    /** 管理端代会员试算（开卡时给前台看这位会员有哪些券可用）。 */
    @Transactional(readOnly = true)
    public CouponQuote quote(Long memberId, Long productId) {
        MemberEntity member = members.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        assertVenue(member.getVenueId());
        MembershipProductEntity product = products.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));
        if (!SecurityUtil.hasRole("MEMBER")) assertVenue(product.getVenueId());
        if (!product.getVenueId().equals(member.getVenueId())
                && !crossVenue.declaredScope(product.getId()).contains(member.getVenueId())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "会员与商品必须属于同一场馆");
        }
        long priceCents = Money.cents(product.getPrice());
        if (!"ACTIVE".equals(product.getStatus())) {
            return new CouponQuote(productId, product.getName(), product.getPrice(), priceCents, 0, List.of());
        }
        List<CouponQuote.Option> options = new ArrayList<>();
        for (MemberCouponEntity coupon : memberCoupons.findByMemberIdOrderByIdDesc(member.getId())) {
            if (evaluate(coupon, member.getId(), product.getVenueId(), priceCents) != null) continue;
            long discount = discountOf(coupon, priceCents);
            options.add(new CouponQuote.Option(coupon.getId(), templateName(coupon),
                    discount, Money.yuan(priceCents - discount)));
        }
        return new CouponQuote(productId, product.getName(), product.getPrice(), priceCents,
                options.size(), options);
    }

    // ------------------------------------------------ 供下单流程调用

    /**
     * 校验这张券此刻是否可用于该会员的这笔订单，可用则返回可抵扣的分。
     * 返回 {@code null} 表示不可用。
     */
    @Transactional(readOnly = true)
    public Long evaluateUsable(Long memberCouponId, Long memberId, Long venueId, long priceCents) {
        MemberCouponEntity coupon = memberCoupons.findById(memberCouponId).orElse(null);
        if (coupon == null) return null;
        String reason = evaluate(coupon, memberId, venueId, priceCents);
        return reason == null ? discountOf(coupon, priceCents) : null;
    }

    /** 下单时校验并解释为什么不可用（给用户可读原因）。 */
    @Transactional(readOnly = true)
    public String unusableReason(Long memberCouponId, Long memberId, Long venueId, long priceCents) {
        MemberCouponEntity coupon = memberCoupons.findById(memberCouponId).orElse(null);
        if (coupon == null) return "优惠券不存在";
        return evaluate(coupon, memberId, venueId, priceCents);
    }

    /** 把券标记为已用并记下抵扣金额与订单号（在订单落库之后调用，同一事务内）。 */
    @Transactional
    public void markUsed(Long memberCouponId, Long orderId, long discountCents) {
        MemberCouponEntity coupon = memberCoupons.findByIdForUpdate(memberCouponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "优惠券不存在"));
        if (!"UNUSED".equals(coupon.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该优惠券已被使用");
        }
        coupon.setStatus("USED");
        coupon.setUsedAt(LocalDateTime.now());
        coupon.setUsedOrderId(orderId);
        coupon.setDiscountCents(discountCents);
        memberCoupons.saveAndFlush(coupon);
    }

    // ---------------------------------------------------------------- 内部

    /** @return 不可用原因；可用返回 {@code null} */
    private String evaluate(MemberCouponEntity coupon, Long memberId, Long venueId, long priceCents) {
        if (!coupon.getMemberId().equals(memberId)) return "该券不属于此会员";
        if (!coupon.getVenueId().equals(venueId)) return "该券不能在其他门店使用";
        if (!"UNUSED".equals(coupon.getStatus())) return "该券已使用";
        LocalDate today = LocalDate.now();
        if (today.isBefore(coupon.getValidFrom())) return "该券尚未生效";
        if (today.isAfter(coupon.getValidUntil())) return "该券已过期";
        CouponTemplateEntity template = templates.findById(coupon.getCouponTemplateId()).orElse(null);
        if (template == null || !"ACTIVE".equals(template.getStatus())) return "该券已停用";
        if (priceCents < template.getThresholdCents()) {
            return "未达到使用门槛（需满 " + Money.yuan(template.getThresholdCents()) + " 元）";
        }
        if (priceCents - discountOf(coupon, priceCents) < 1) return "优惠后金额过低，该券不适用于此商品";
        return null;
    }

    /** 按分计算抵扣额，且不会把订单打成负数。 */
    private long discountOf(MemberCouponEntity coupon, long priceCents) {
        CouponTemplateEntity template = templates.findById(coupon.getCouponTemplateId()).orElse(null);
        if (template == null) return 0L;
        if (TYPE_FULL_REDUCTION.equals(template.getCouponType())) {
            return Math.min(template.getDiscountCents(), priceCents);
        }
        BigDecimal rate = template.getDiscountRate();
        if (rate == null) return 0L;
        long payable = BigDecimal.valueOf(priceCents).multiply(rate)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return priceCents - payable;
    }

    private String templateName(MemberCouponEntity coupon) {
        return templates.findById(coupon.getCouponTemplateId())
                .map(CouponTemplateEntity::getName).orElse("优惠券");
    }

    private MemberCouponView toView(MemberCouponEntity coupon) {
        CouponTemplateEntity template = templates.findById(coupon.getCouponTemplateId()).orElse(null);
        // 列表展示不针对具体商品，所以只看“是否已用/是否过期”，门槛与折扣在结算时判断
        boolean expired = LocalDate.now().isAfter(coupon.getValidUntil());
        String status = "UNUSED".equals(coupon.getStatus()) && expired ? "EXPIRED" : coupon.getStatus();
        boolean usable = "UNUSED".equals(coupon.getStatus()) && !expired;
        String unusableReason = usable ? null
                : ("USED".equals(coupon.getStatus()) ? "已使用" : "已过期");
        return new MemberCouponView(coupon.getId(), coupon.getCouponTemplateId(),
                template == null ? "优惠券" : template.getName(),
                template == null ? null : template.getCouponType(),
                template == null ? null : template.getThresholdCents(),
                template == null ? null : template.getDiscountCents(),
                template == null ? null : template.getDiscountRate(),
                coupon.getCode(), status, coupon.getValidFrom(), coupon.getValidUntil(),
                coupon.getDiscountCents(), coupon.getUsedAt(), coupon.getUsedOrderId(),
                usable, unusableReason);
    }

    private TemplateView toTemplateView(CouponTemplateEntity entity) {
        return new TemplateView(entity.getId(), entity.getVenueId(), entity.getName(), entity.getCouponType(),
                entity.getThresholdCents(), entity.getDiscountCents(), entity.getDiscountRate(),
                entity.getTotalQuantity(), entity.getIssuedQuantity(), entity.getValidFrom(),
                entity.getValidUntil(), entity.getStatus(), entity.getNote());
    }

    private MemberEntity currentMember() {
        return members.findByUserId(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Long requireVenue() {
        Long venueId = SecurityUtil.getCurrentOrganizationId();
        if (venueId == null) throw new BusinessException(ErrorCode.FORBIDDEN, "缺少门店上下文");
        return venueId;
    }

    private void assertVenue(Long venueId) {
        if (!SecurityUtil.isSuperAdmin() && !requireVenue().equals(venueId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能操作其他门店的数据");
        }
    }

    private Long resolveVenueId(Long requested) {
        if (SecurityUtil.isSuperAdmin() && requested != null) return requested;
        if (SecurityUtil.isSuperAdmin()) return requireVenue();
        return requireVenue();
    }
}
