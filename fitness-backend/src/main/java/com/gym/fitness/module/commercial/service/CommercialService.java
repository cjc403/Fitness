package com.gym.fitness.module.commercial.service;

import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.common.util.SecurityUtil;
import com.gym.fitness.module.commercial.dto.CommercialDtos.*;
import com.gym.fitness.module.commercial.entity.*;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommercialService {
    private final MembershipProductRepository products;
    private final SalesOrderRepository orders;
    private final MemberEntitlementRepository entitlements;
    private final EntitlementOperationRepository operations;
    private final PaymentTransactionRepository payments;
    private final BusinessDocumentRepository documents;
    private final AccessRecordRepository accessRecords;
    private final MemberRepository members;
    private final VenueRepository venues;
    private final com.gym.fitness.module.training.repository.PrivateLessonRepository privateLessons;
    private final jakarta.persistence.EntityManager entityManager;
    private final com.gym.fitness.module.finance.service.EntitlementValueService values;
    private final com.gym.fitness.module.finance.service.FinanceReportService financeReports;
    private final com.gym.fitness.common.util.VenueMutationLock venueLock;
    private final AccessGrantRepository accessGrants;
    private final com.gym.fitness.module.notice.service.NotificationService notifications;

    /** 门禁二维码有效期：够走到前台，不够被截图后复用。 */
    static final long ACCESS_GRANT_TTL_SECONDS = 60;

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        return scopedProducts().stream().map(this::toProduct).toList();
    }

    /**
     * 会员端会籍商城的商品列表：**与员工端同一套在售商品**（含其他门店），
     * 但由服务端标记 purchasable——会员只能购买本人所属门店的商品，
     * 因为 createSale 要求"会员与商品必须属于同一场馆"。其他门店的商品只展示、不可下单。
     */
    @Transactional(readOnly = true)
    public List<MemberProductView> listMemberShopProducts() {
        Long memberVenueId = requireVenue();
        Map<Long, String> venueNames = venues.findAll().stream()
                .collect(Collectors.toMap(venue -> venue.getId(), venue -> venue.getName()));
        return products.findByStatusOrderByIdDesc("ACTIVE").stream()
                .map(item -> new MemberProductView(item.getId(), item.getName(), item.getProductType(),
                        item.getDescription(), item.getValidityDays(), item.getTotalUses(), item.getPrice(),
                        item.getVenueId(), venueNames.get(item.getVenueId()),
                        memberVenueId.equals(item.getVenueId())))
                .toList();
    }

    /**
     * 会员自助购买（不接入真实支付）。金额一律取服务端商品标价，支付渠道固定标记为 SELF_SERVICE，
     * 避免会员在请求里篡改金额。其余链路与前台开卡完全一致（订单、权益、价值账户、流水、合同、收据）。
     */
    @Transactional
    public OrderResponse createSelfServiceSale(Long memberId, Long productId, String idempotencyKey) {
        SaleRequest request = new SaleRequest();
        request.setMemberId(memberId);
        request.setProductId(productId);
        request.setPaymentMethod("SELF_SERVICE");
        request.setPaidAmount(null);
        request.setRemark("会员自助购买（未接入真实支付）");
        request.setIdempotencyKey(idempotencyKey);
        return createSale(request);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Long venueId = resolveVenue(request.getVenueId());
        if (products.existsByVenueIdAndNameIgnoreCase(venueId, request.getName().trim())) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS, "本场馆已存在同名商品");
        }
        MembershipProductEntity product = new MembershipProductEntity();
        applyProduct(product, request, venueId);
        product.setCreatedBy(SecurityUtil.getCurrentUserId());
        return toProduct(products.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        MembershipProductEntity product = accessibleProduct(id);
        Long venueId = isSuperAdmin() && request.getVenueId() != null ? request.getVenueId() : product.getVenueId();
        products.findByVenueIdOrderByIdDesc(venueId).stream()
                .filter(existing -> !existing.getId().equals(id))
                .filter(existing -> existing.getName().equalsIgnoreCase(request.getName().trim()))
                .findFirst().ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS, "本场馆已存在同名商品");
                });
        String previousStatus = product.getStatus();
        applyProduct(product, request, venueId);
        ProductResponse saved = toProduct(products.save(product));
        // 商品下架后会员端不再展示，已购权益不受影响——但要主动告诉持卡会员，避免他们以为权益没了。
        if (!"INACTIVE".equals(previousStatus) && "INACTIVE".equals(saved.status())) {
            List<Long> holders = entitlements.findByVenueIdOrderByIdDesc(venueId).stream()
                    .filter(item -> "ACTIVE".equals(item.getStatus()))
                    .filter(item -> product.getId().equals(item.getProductId()))
                    .map(MemberEntitlementEntity::getMemberId).distinct().toList();
            notifications.notifyMembers(holders, venueId, "PRODUCT_OFF_SHELF",
                    "商品已下架：" + saved.name(),
                    "该商品已下架、不再对外销售。你已购买的权益不受影响，可以继续正常使用。",
                    "PRODUCT", product.getId());
        }
        return saved;
    }

    /**
     * 删除会籍商品。sales_order 与 member_entitlement 都以 NO ACTION 外键引用商品，
     * 一旦卖过就不能物理删除（会破坏订单、权益与财务报表的历史），
     * 因此这里在业务层拦下并给出可读原因，提示改用「下架」。
     */
    @Transactional
    public void deleteProduct(Long id) {
        MembershipProductEntity product = accessibleProduct(id);
        long orderCount = orders.countByProductId(id);
        long entitlementCount = entitlements.countByProductId(id);
        if (orderCount > 0 || entitlementCount > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "该商品已有 " + orderCount + " 笔订单、" + entitlementCount + " 份权益，不能删除；请改为下架");
        }
        products.delete(product);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders() {
        List<SalesOrderEntity> list = isSuperAdmin() ? orders.findAllByOrderByCreatedAtDesc()
                : orders.findByVenueIdOrderByCreatedAtDesc(requireVenue());
        return enrichOrders(list);
    }

    @Transactional
    public OrderResponse createSale(SaleRequest request) {
        MemberEntity member = accessibleMember(request.getMemberId());
        venueLock.acquire(member.getVenueId());
        // 幂等：同一操作者用同一个键重试只会落一单（连点、超时重发都不会多开一张卡）。
        // 放在场馆锁之后，保证并发下也只有一个请求能看到"不存在已有订单"。
        Long operatorId = SecurityUtil.getCurrentUserId();
        String fingerprint = saleFingerprint(request);
        Optional<SalesOrderEntity> replay = orders.findByOperatorIdAndIdempotencyKey(operatorId, request.getIdempotencyKey());
        if (replay.isPresent()) {
            if (!fingerprint.equals(replay.get().getRequestFingerprint())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "该幂等键已用于不同的销售内容，请刷新后重新提交");
            }
            return enrichOrders(List.of(replay.get())).get(0);
        }
        values.assertBookableMember(member.getId());
        MembershipProductEntity product = accessibleProduct(request.getProductId());
        if (!member.getVenueId().equals(product.getVenueId())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "会员与商品必须属于同一场馆");
        }
        if (!"ACTIVE".equals(member.getStatus()) || Boolean.TRUE.equals(member.getBlacklistFlag())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该会员当前不能办理开卡");
        }
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "商品已下架");
        }
        BigDecimal paid = request.getPaidAmount() == null ? product.getPrice() : request.getPaidAmount();
        if (paid.compareTo(product.getPrice()) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "实收金额不能高于商品标价");
        }
        SalesOrderEntity order = paidOrder("NEW", member, product, paid, request.getPaymentMethod(), request.getRemark());
        order.setIdempotencyKey(request.getIdempotencyKey());
        order.setRequestFingerprint(fingerprint);
        orders.saveAndFlush(order);
        MemberEntitlementEntity entitlement = issueEntitlement(member, product, order);
        values.issue(entitlement, order, product.getProductType());
        recordOperation(entitlement, "ISSUE", null, "ACTIVE", paid, "销售开卡");
        recordPayment(order, "PAYMENT", paid, request.getPaymentMethod());
        createDocument(order, member, "CONTRACT", "会员服务合同", null);
        createDocument(order, member, "RECEIPT", "FitChain 收款收据", null);
        return enrichOrders(List.of(order)).get(0);
    }

    /** 幂等指纹：同一键配不同内容要能识别出来，避免"用旧键改了内容"被静默复用。 */
    private String saleFingerprint(SaleRequest request) {
        String raw = request.getMemberId() + "|" + request.getProductId() + "|"
                + (request.getPaidAmount() == null ? "" : request.getPaidAmount().toPlainString()) + "|"
                + request.getPaymentMethod().trim().toUpperCase(Locale.ROOT) + "|"
                + (request.getRemark() == null ? "" : request.getRemark().trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public List<EntitlementResponse> listEntitlements(Long memberId, boolean remindersOnly) {
        List<MemberEntitlementEntity> list;
        if (memberId != null) {
            accessibleMember(memberId);
            list = entitlements.findByMemberIdOrderByIdDesc(memberId);
        } else {
            list = isSuperAdmin() ? entitlements.findAllByOrderByIdDesc()
                    : entitlements.findByVenueIdOrderByIdDesc(requireVenue());
        }
        refreshExpired(list);
        return enrichEntitlements(list).stream()
                .filter(item -> !remindersOnly || !"NONE".equals(item.reminderLevel()))
                .toList();
    }

    @Transactional
    public EntitlementResponse freeze(Long id, NoteRequest request) {
        MemberEntitlementEntity item = accessibleEntitlement(id);
        assertNoPendingLessons(item);
        requireStatus(item, "ACTIVE", "只有生效中的权益可以冻结");
        values.pause(item);
        String before = item.getStatus();
        item.setStatus("FROZEN");
        item.setFrozenAt(com.gym.fitness.module.finance.service.EntitlementValueService.now());
        entitlements.save(item);
        recordOperation(item, "FREEZE", before, item.getStatus(), null, safeNote(request, "会员申请冻结"));
        return enrichEntitlements(List.of(item)).get(0);
    }

    @Transactional
    public EntitlementResponse unfreeze(Long id, NoteRequest request) {
        MemberEntitlementEntity item = accessibleEntitlement(id);
        requireStatus(item, "FROZEN", "只有冻结中的权益可以解冻");
        long days = values.resume(item);
        String before = item.getStatus();
        item.setStatus("ACTIVE");
        item.setValidUntil(item.getValidUntil().plusDays(days));
        item.setAccumulatedFrozenDays(item.getAccumulatedFrozenDays() + (int) days);
        item.setFrozenAt(null);
        entitlements.save(item);
        recordOperation(item, "UNFREEZE", before, item.getStatus(), null,
                safeNote(request, "解冻并顺延 " + days + " 天"));
        return enrichEntitlements(List.of(item)).get(0);
    }

    @Transactional
    public EntitlementResponse renew(Long id, RenewalRequest request) {
        MemberEntitlementEntity item = accessibleEntitlement(id);
        if ("FROZEN".equals(item.getStatus())) throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "请先解冻再续费");
        if (List.of("REFUNDED", "TRANSFERRED").contains(item.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该权益不能续费");
        }
        MemberEntity member = members.findById(item.getMemberId()).orElseThrow();
        MembershipProductEntity product = products.findById(item.getProductId()).orElseThrow();
        values.refreshTime(item);
        int addedDays = request.getAdditionalDays() == null ? product.getValidityDays() : request.getAdditionalDays();
        int addedUses = request.getAdditionalUses() == null ? Optional.ofNullable(product.getTotalUses()).orElse(0) : request.getAdditionalUses();
        LocalDate termStart = item.getValidUntil().isBefore(com.gym.fitness.module.finance.service.EntitlementValueService.today())
                ? com.gym.fitness.module.finance.service.EntitlementValueService.today() : item.getValidUntil().plusDays(1);
        SalesOrderEntity order = paidOrder("RENEW", member, product, request.getAmount(),
                request.getPaymentMethod(), request.getNote());
        String before = item.getStatus();
        item.setValidUntil(termStart.plusDays(addedDays - 1L));
        if (item.getRemainingUses() != null) {
            int add = request.getAdditionalUses() == null
                    ? Optional.ofNullable(product.getTotalUses()).orElse(0) : request.getAdditionalUses();
            if ("PRIVATE_PACKAGE".equals(values.productType(item)) && add > 0) {
                BigDecimal oldUnit = item.getLessonUnitPrice() == null
                        ? orders.findById(item.getSourceOrderId()).orElseThrow().getPaidAmount().divide(BigDecimal.valueOf(item.getTotalUses()), 2, java.math.RoundingMode.HALF_UP)
                        : item.getLessonUnitPrice();
                item.setLessonUnitPrice(oldUnit.multiply(BigDecimal.valueOf(item.getRemainingUses())).add(request.getAmount())
                        .divide(BigDecimal.valueOf(item.getRemainingUses() + add), 2, java.math.RoundingMode.HALF_UP));
            }
            item.setTotalUses(Optional.ofNullable(item.getTotalUses()).orElse(0) + add);
            item.setRemainingUses(item.getRemainingUses() + add);
        }
        item.setStatus("ACTIVE");
        entitlements.save(item);
        values.addFunding(item, order, item.getRemainingUses() == null ? addedDays : addedUses, termStart, "RENEW");
        recordPayment(order, "PAYMENT", request.getAmount(), request.getPaymentMethod());
        createDocument(order, member, "RECEIPT", "FitChain 续费收据", null);
        recordOperation(item, "RENEW", before, "ACTIVE", request.getAmount(),
                request.getNote() == null ? "会员续费" : request.getNote());
        return enrichEntitlements(List.of(item)).get(0);
    }

    @Transactional
    public EntitlementResponse transfer(Long id, TransferRequest request) {
        MemberEntitlementEntity item = accessibleEntitlement(id);
        assertNoPendingLessons(item);
        if (!List.of("ACTIVE", "FROZEN").contains(item.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "当前状态不允许转卡");
        }
        MemberEntity target = accessibleMember(request.getTargetMemberId());
        if (!target.getVenueId().equals(item.getVenueId()) || target.getId().equals(item.getMemberId())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "只能转给本场馆的其他会员");
        }
        if (!"ACTIVE".equals(target.getStatus()) || Boolean.TRUE.equals(target.getBlacklistFlag())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "接收会员当前不可办理转卡");
        }
        Long oldMember = item.getMemberId();
        item.setTransferredFromMemberId(oldMember);
        item.setMemberId(target.getId());
        entitlements.save(item);
        values.transferred(item);
        recordOperation(item, "TRANSFER", item.getStatus(), item.getStatus(), null,
                request.getNote() == null ? "权益转让，原会员ID：" + oldMember : request.getNote());
        return enrichEntitlements(List.of(item)).get(0);
    }

    @Transactional
    public EntitlementResponse refund(Long id, RefundRequest request) {
        throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "直接退款入口已停用，请通过退款申请、审核及支付登记流程办理");
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments() {
        List<SalesOrderEntity> scoped = scopedOrders();
        if (scoped.isEmpty()) return List.of();
        Map<Long, SalesOrderEntity> orderMap = scoped.stream().collect(Collectors.toMap(SalesOrderEntity::getId, Function.identity()));
        return payments.findByOrderIdInOrderByOccurredAtDesc(new ArrayList<>(orderMap.keySet())).stream()
                .map(item -> new PaymentResponse(item.getId(), item.getTransactionNo(), item.getOrderId(),
                        orderMap.get(item.getOrderId()).getOrderNo(), item.getTransactionType(), item.getAmount(),
                        item.getPaymentMethod(), item.getStatus(), item.getOccurredAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments() {
        List<SalesOrderEntity> scoped = scopedOrders();
        if (scoped.isEmpty()) return List.of();
        Map<Long, SalesOrderEntity> orderMap = scoped.stream().collect(Collectors.toMap(SalesOrderEntity::getId, Function.identity()));
        Map<Long, MemberEntity> memberMap = members.findAllById(scoped.stream().map(SalesOrderEntity::getMemberId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MemberEntity::getId, Function.identity()));
        return documents.findByOrderIdInOrderByCreatedAtDesc(new ArrayList<>(orderMap.keySet())).stream()
                .map(item -> new DocumentResponse(item.getId(), item.getDocumentNo(), item.getDocumentType(),
                        item.getOrderId(), orderMap.get(item.getOrderId()).getOrderNo(),
                        memberMap.get(item.getMemberId()).getRealName(), item.getTitle(), item.getTaxNo(),
                        item.getAmount(), item.getStatus(), item.getIssuedAt())).toList();
    }

    @Transactional
    public DocumentResponse issueInvoice(Long orderId, InvoiceRequest request) {
        SalesOrderEntity order = accessibleOrder(orderId);
        if (documents.existsByOrderIdAndDocumentType(orderId, "INVOICE")) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS, "该订单已开具发票");
        }
        MemberEntity member = members.findById(order.getMemberId()).orElseThrow();
        BusinessDocumentEntity document = createDocument(order, member, "INVOICE", request.getTitle(), request.getTaxNo());
        return new DocumentResponse(document.getId(), document.getDocumentNo(), document.getDocumentType(),
                order.getId(), order.getOrderNo(), member.getRealName(), document.getTitle(), document.getTaxNo(),
                document.getAmount(), document.getStatus(), document.getIssuedAt());
    }

    @Transactional
    public AccessCodeResponse myAccessCode() {
        Long userId = SecurityUtil.getCurrentUserId();
        MemberEntity member = members.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        // 每次取码都签发一个短时效一次性令牌，替代此前永不过期的 member.access_token。
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusSeconds(ACCESS_GRANT_TTL_SECONDS);
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        AccessGrantEntity grant = new AccessGrantEntity();
        grant.setMemberId(member.getId());
        grant.setVenueId(member.getVenueId());
        grant.setToken(token);
        grant.setIssuedAt(issuedAt);
        grant.setExpiresAt(expiresAt);
        accessGrants.save(grant);
        return new AccessCodeResponse(member.getMemberNo(), member.getRealName(), token,
                "FITCHAIN://ACCESS/" + token, issuedAt, expiresAt, ACCESS_GRANT_TTL_SECONDS);
    }

    @Transactional
    public AccessResponse scanAccess(AccessScanRequest request) {
        String token = request.getToken().trim().replace("FITCHAIN://ACCESS/", "");
        // 只认短时效一次性令牌；此前那种"永久会员令牌"不再可用。
        AccessGrantEntity grant = accessGrants.findByTokenForUpdate(token).orElse(null);
        MemberEntity member = grant == null ? null : members.findById(grant.getMemberId()).orElse(null);
        Long scanVenue = grant == null ? requireVenue() : grant.getVenueId();
        if (!isSuperAdmin() && !scanVenue.equals(requireVenue())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能核销其他场馆的会员");
        }
        venueLock.acquire(scanVenue);
        AccessRecordEntity record = new AccessRecordEntity();
        record.setVenueId(scanVenue);
        record.setDirection(request.getDirection() == null ? "IN" : request.getDirection().toUpperCase());
        record.setOperatorId(SecurityUtil.getCurrentUserId());
        record.setAccessTokenMasked(mask(token));
        if (grant == null || member == null) {
            record.setResult("DENIED");
            record.setReason("无效二维码");
            return toAccess(accessRecords.save(record), null, null);
        }
        if (grant.getExpiresAt().isBefore(LocalDateTime.now())) {
            record.setResult("DENIED");
            record.setReason("二维码已过期，请刷新会员端二维码");
            return toAccess(accessRecords.save(record), null, null);
        }
        if (grant.getUsedAt() != null) {
            record.setResult("DENIED");
            record.setReason("该二维码已被使用，请刷新会员端二维码");
            return toAccess(accessRecords.save(record), null, null);
        }
        record.setMemberId(member.getId());
        if (!"ACTIVE".equals(member.getStatus()) || Boolean.TRUE.equals(member.getBlacklistFlag())) {
            record.setResult("DENIED");
            record.setReason("会员状态不可用");
            return toAccess(accessRecords.save(record), member, null);
        }
        if ("OUT".equals(record.getDirection())) {
            record.setResult("APPROVED");
            record.setReason("离场成功");
            consumeGrant(grant, scanVenue);
            return toAccess(accessRecords.save(record), member, null);
        }
        LocalDate today = LocalDate.now();
        MemberEntitlementEntity available = entitlements.findByMemberIdAndStatusOrderByValidUntilAsc(member.getId(), "ACTIVE")
                .stream().filter(item -> !today.isBefore(item.getValidFrom()) && !today.isAfter(item.getValidUntil()))
                .filter(item -> {
                    MembershipProductEntity product = products.findById(item.getProductId()).orElse(null);
                    return product != null && Boolean.TRUE.equals(product.getAccessAllowed())
                            && (item.getRemainingUses() == null || item.getRemainingUses() > 0);
                }).findFirst().orElse(null);
        if (available == null) {
            record.setResult("DENIED");
            record.setReason("没有可用的入场权益");
            return toAccess(accessRecords.save(record), member, null);
        }
        available=entitlements.findByIdForUpdate(available.getId()).orElseThrow();
        entityManager.refresh(available);
        values.assertMutable(available);
        values.refreshTime(available);
        if(!"ACTIVE".equals(available.getStatus())||!Objects.equals(available.getMemberId(),member.getId())||today.isBefore(available.getValidFrom())||today.isAfter(available.getValidUntil())||(available.getRemainingUses()!=null&&available.getRemainingUses()<1)){
            record.setResult("DENIED");record.setReason("入场权益已变化，请重新核验");return toAccess(accessRecords.save(record),member,null);
        }
        record.setEntitlementId(available.getId());
        record.setResult("APPROVED");
        record.setReason("入场成功");
        consumeGrant(grant, scanVenue);
        if (available.getRemainingUses() != null && !"PRIVATE_PACKAGE".equals(values.productType(available))) {
            accessRecords.saveAndFlush(record);
            values.consume(available, "ACCESS:" + record.getId(), BigDecimal.ZERO);
            available.setRemainingUses(Math.max(0, available.getRemainingUses() - 1));
            if (available.getRemainingUses() == 0) available.setStatus("USED_UP");
            entitlements.save(available);
            recordOperation(available, "DEDUCT", "ACTIVE", available.getStatus(), null, "二维码入场核销 1 次");
        }
        return toAccess(accessRecords.save(record), member, available);
    }

    /** 扫码通过后把令牌标记为已用，同一个二维码不能再用第二次。 */
    private void consumeGrant(AccessGrantEntity grant, Long scanVenue) {
        grant.setUsedAt(LocalDateTime.now());
        grant.setUsedBy(SecurityUtil.getCurrentUserId());
        grant.setUsedVenueId(scanVenue);
        accessGrants.saveAndFlush(grant);
    }

    @Transactional(readOnly = true)
    public List<AccessResponse> listAccessRecords() {
        List<AccessRecordEntity> list = isSuperAdmin() ? accessRecords.findTop100ByOrderByScannedAtDesc()
                : accessRecords.findTop100ByVenueIdOrderByScannedAtDesc(requireVenue());
        Map<Long, MemberEntity> memberMap = members.findAllById(list.stream().map(AccessRecordEntity::getMemberId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(MemberEntity::getId, Function.identity()));
        Map<Long, MemberEntitlementEntity> entitlementMap = entitlements.findAllById(list.stream()
                .map(AccessRecordEntity::getEntitlementId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MemberEntitlementEntity::getId, Function.identity()));
        return list.stream().map(item -> toAccess(item, memberMap.get(item.getMemberId()),
                entitlementMap.get(item.getEntitlementId()))).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Long venueId = isSuperAdmin() ? null : requireVenue();
        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        List<SalesOrderEntity> monthOrders = venueId == null
                ? orders.findByPaidAtBetween(monthStart, LocalDateTime.now().plusSeconds(1))
                : orders.findByVenueIdAndPaidAtBetween(venueId, monthStart, LocalDateTime.now().plusSeconds(1));
        BigDecimal revenue = financeReports.netCash(today.withDayOfMonth(1), today);
        long active = venueId == null ? entitlements.countByStatus("ACTIVE")
                : entitlements.countByVenueIdAndStatus(venueId, "ACTIVE");
        long expiring = venueId == null
                ? entitlements.countByStatusAndValidUntilBetween("ACTIVE", today, today.plusDays(30))
                : entitlements.countByVenueIdAndStatusAndValidUntilBetween(venueId, "ACTIVE", today, today.plusDays(30));
        long entries = venueId == null
                ? accessRecords.countByResultAndScannedAtBetween("APPROVED", today.atStartOfDay(), today.plusDays(1).atStartOfDay())
                : accessRecords.countByVenueIdAndResultAndScannedAtBetween(venueId, "APPROVED", today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monthRevenue", revenue);
        result.put("monthOrders", monthOrders.size());
        result.put("activeEntitlements", active);
        result.put("expiringWithin30Days", expiring);
        result.put("todayEntries", entries);
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    @Transactional
    public List<EntitlementResponse> myEntitlements() {
        MemberEntity member = members.findByUserId(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        List<MemberEntitlementEntity> list = entitlements.findByMemberIdOrderByIdDesc(member.getId());
        refreshExpired(list);
        return enrichEntitlements(list);
    }

    private SalesOrderEntity paidOrder(String type, MemberEntity member, MembershipProductEntity product,
                                        BigDecimal paid, String method, String remark) {
        com.gym.fitness.module.finance.service.Money.cents(paid);
        SalesOrderEntity order = new SalesOrderEntity();
        order.setOrderNo(number("SO"));
        order.setOrderType(type);
        order.setMemberId(member.getId());
        order.setProductId(product.getId());
        order.setVenueId(member.getVenueId());
        order.setOriginalAmount(product.getPrice());
        order.setPaidAmount(paid);
        order.setRefundedAmount(BigDecimal.ZERO);
        order.setPaymentMethod(method.toUpperCase());
        order.setStatus("PAID");
        order.setRemark(remark);
        order.setOperatorId(SecurityUtil.getCurrentUserId());
        order.setPaidAt(LocalDateTime.now());
        return orders.save(order);
    }

    private MemberEntitlementEntity issueEntitlement(MemberEntity member, MembershipProductEntity product,
                                                       SalesOrderEntity order) {
        MemberEntitlementEntity item = new MemberEntitlementEntity();
        item.setEntitlementNo(number("ME"));
        item.setMemberId(member.getId());
        item.setProductId(product.getId());
        item.setSourceOrderId(order.getId());
        item.setVenueId(member.getVenueId());
        item.setValidFrom(com.gym.fitness.module.finance.service.EntitlementValueService.today());
        item.setValidUntil(com.gym.fitness.module.finance.service.EntitlementValueService.today().plusDays(product.getValidityDays() - 1L));
        item.setTotalUses(product.getTotalUses());
        item.setRemainingUses(product.getTotalUses());
        if ("PRIVATE_PACKAGE".equals(product.getProductType()) && product.getTotalUses() != null)
            item.setLessonUnitPrice(order.getPaidAmount().divide(BigDecimal.valueOf(product.getTotalUses()), 2, java.math.RoundingMode.HALF_UP));
        item.setStatus("ACTIVE");
        item.setAccumulatedFrozenDays(0);
        return entitlements.save(item);
    }

    private void applyProduct(MembershipProductEntity product, ProductRequest request, Long venueId) {
        com.gym.fitness.module.finance.service.Money.cents(request.getPrice());
        if (!venues.existsById(venueId)) throw new BusinessException(ErrorCode.VENUE_NOT_FOUND);
        String category = request.getCategory().toUpperCase();
        if (!List.of("CARD", "PACKAGE").contains(category)) throw new BusinessException(ErrorCode.PARAM_ERROR, "商品分类不正确");
        if ("PACKAGE".equals(category) && request.getTotalUses() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "课包或次卡必须填写总次数");
        }
        product.setName(request.getName().trim());
        product.setCategory(category);
        product.setProductType(request.getProductType().toUpperCase());
        product.setDescription(request.getDescription());
        product.setValidityDays(request.getValidityDays());
        product.setTotalUses(request.getTotalUses());
        product.setAccessAllowed(request.getAccessAllowed() == null || request.getAccessAllowed());
        product.setPrice(request.getPrice());
        product.setVenueId(venueId);
        product.setStatus(request.getStatus() == null ? "ACTIVE" : request.getStatus().toUpperCase());
    }

    private void recordPayment(SalesOrderEntity order, String type, BigDecimal amount, String method) {
        PaymentTransactionEntity payment = new PaymentTransactionEntity();
        payment.setTransactionNo(number("TX"));
        payment.setOrderId(order.getId());
        payment.setTransactionType(type);
        payment.setAmount(amount);
        payment.setPaymentMethod(method.toUpperCase());
        payment.setStatus("SUCCESS");
        payment.setOperatorId(SecurityUtil.getCurrentUserId());
        payments.save(payment);
    }

    private BusinessDocumentEntity createDocument(SalesOrderEntity order, MemberEntity member, String type,
                                                    String title, String taxNo) {
        BusinessDocumentEntity document = new BusinessDocumentEntity();
        document.setDocumentNo(number(switch (type) { case "CONTRACT" -> "CT"; case "INVOICE" -> "IV"; default -> "RC"; }));
        document.setDocumentType(type);
        document.setOrderId(order.getId());
        document.setMemberId(member.getId());
        document.setTitle(title);
        document.setTaxNo(taxNo);
        document.setAmount(order.getPaidAmount());
        document.setStatus("ISSUED");
        document.setIssuedAt(LocalDateTime.now());
        document.setCreatedBy(SecurityUtil.getCurrentUserId());
        return documents.save(document);
    }

    private void recordOperation(MemberEntitlementEntity item, String type, String before, String after,
                                 BigDecimal amount, String note) {
        EntitlementOperationEntity operation = new EntitlementOperationEntity();
        operation.setEntitlementId(item.getId());
        operation.setOperationType(type);
        operation.setBeforeStatus(before);
        operation.setAfterStatus(after);
        operation.setAmount(amount);
        operation.setNote(note);
        operation.setOperatorId(SecurityUtil.getCurrentUserId());
        operations.save(operation);
    }

    private List<OrderResponse> enrichOrders(List<SalesOrderEntity> list) {
        Map<Long, MemberEntity> memberMap = members.findAllById(list.stream().map(SalesOrderEntity::getMemberId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MemberEntity::getId, Function.identity()));
        Map<Long, MembershipProductEntity> productMap = products.findAllById(list.stream().map(SalesOrderEntity::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MembershipProductEntity::getId, Function.identity()));
        return list.stream().map(item -> {
            MemberEntity member = memberMap.get(item.getMemberId());
            MembershipProductEntity product = productMap.get(item.getProductId());
            return new OrderResponse(item.getId(), item.getOrderNo(), item.getOrderType(), item.getMemberId(),
                    member.getMemberNo(), member.getRealName(), item.getProductId(), product.getName(), item.getVenueId(),
                    item.getOriginalAmount(), item.getPaidAmount(), item.getRefundedAmount(), item.getPaymentMethod(),
                    item.getStatus(), item.getRemark(), item.getPaidAt(), item.getCreatedAt());
        }).toList();
    }

    private List<EntitlementResponse> enrichEntitlements(List<MemberEntitlementEntity> list) {
        refreshExpired(list);
        Map<Long, MemberEntity> memberMap = members.findAllById(list.stream().map(MemberEntitlementEntity::getMemberId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MemberEntity::getId, Function.identity()));
        Map<Long, MembershipProductEntity> productMap = products.findAllById(list.stream().map(MemberEntitlementEntity::getProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MembershipProductEntity::getId, Function.identity()));
        LocalDate today = LocalDate.now();
        return list.stream().map(item -> {
            MemberEntity member = memberMap.get(item.getMemberId());
            MembershipProductEntity product = productMap.get(item.getProductId());
            int days = (int) ChronoUnit.DAYS.between(today, item.getValidUntil());
            String reminder = reminder(item, days);
            return new EntitlementResponse(item.getId(), item.getEntitlementNo(), item.getMemberId(),
                    member.getMemberNo(), member.getRealName(), item.getProductId(), product.getName(),
                    values.productType(item), product.getAccessAllowed(), item.getSourceOrderId(), item.getVenueId(),
                    item.getValidFrom(), item.getValidUntil(), item.getTotalUses(), item.getRemainingUses(), item.getStatus(),
                    item.getFrozenAt(), item.getAccumulatedFrozenDays(), days, reminder, item.getReservedUses(),
                    item.getRemainingUses() == null ? null : item.getRemainingUses() - item.getReservedUses());
        }).toList();
    }

    private String reminder(MemberEntitlementEntity item, int days) {
        if (item.getRemainingUses() != null && item.getRemainingUses() <= 3) return "LOW_BALANCE";
        if (days < 0 || "EXPIRED".equals(item.getStatus())) return "EXPIRED";
        if (days <= 7) return "EXPIRING_7_DAYS";
        if (days <= 30) return "EXPIRING_30_DAYS";
        return "NONE";
    }

    private void refreshExpired(List<MemberEntitlementEntity> list) {
        LocalDate today = LocalDate.now();
        list.stream().filter(item -> "ACTIVE".equals(item.getStatus()) && item.getValidUntil().isBefore(today))
                .sorted(Comparator.comparing(MemberEntitlementEntity::getId))
                .forEach(item -> {venueLock.acquire(item.getVenueId());var locked=entitlements.findByIdForUpdate(item.getId()).orElseThrow();entityManager.refresh(locked);if(values.account(locked).getActiveRefundId()==null&&"ACTIVE".equals(locked.getStatus())&&locked.getValidUntil().isBefore(today)){locked.setStatus("EXPIRED");entitlements.save(locked);} });
    }

    private ProductResponse toProduct(MembershipProductEntity item) {
        return new ProductResponse(item.getId(), item.getName(), item.getCategory(), item.getProductType(),
                item.getDescription(), item.getValidityDays(), item.getTotalUses(), item.getAccessAllowed(),
                item.getPrice(), item.getVenueId(), item.getStatus());
    }

    private AccessResponse toAccess(AccessRecordEntity item, MemberEntity member, MemberEntitlementEntity entitlement) {
        return new AccessResponse(item.getId(), item.getMemberId(), member == null ? null : member.getMemberNo(),
                member == null ? null : member.getRealName(), item.getEntitlementId(),
                entitlement == null ? null : entitlement.getEntitlementNo(), item.getVenueId(), item.getDirection(),
                item.getResult(), item.getReason(), item.getScannedAt());
    }

    private List<MembershipProductEntity> scopedProducts() {
        return isSuperAdmin() ? products.findAllByOrderByIdDesc() : products.findByVenueIdOrderByIdDesc(requireVenue());
    }

    private List<SalesOrderEntity> scopedOrders() {
        return isSuperAdmin() ? orders.findAllByOrderByCreatedAtDesc() : orders.findByVenueIdOrderByCreatedAtDesc(requireVenue());
    }

    private MembershipProductEntity accessibleProduct(Long id) {
        MembershipProductEntity item = products.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));
        assertVenue(item.getVenueId()); return item;
    }

    private SalesOrderEntity accessibleOrder(Long id) {
        SalesOrderEntity item = orders.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单不存在"));
        assertVenue(item.getVenueId()); return item;
    }

    private MemberEntitlementEntity accessibleEntitlement(Long id) {
        MemberEntitlementEntity item = values.lock(id);
        values.assertMutable(item);
        return item;
    }

    private void assertNoPendingLessons(MemberEntitlementEntity item) {
        if (item.getReservedUses() > 0)
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该课包有未结束预约或待签课，请先处理后再办理");
    }

    private MemberEntity accessibleMember(Long id) {
        MemberEntity member = members.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        assertVenue(member.getVenueId()); return member;
    }

    private Long resolveVenue(Long requested) {
        if (isSuperAdmin()) {
            if (requested == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择所属场馆");
            return requested;
        }
        return requireVenue();
    }

    private void assertVenue(Long venueId) {
        if (!isSuperAdmin() && !Objects.equals(requireVenue(), venueId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private Long requireVenue() {
        Long id = SecurityUtil.getCurrentOrganizationId();
        if (id == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return id;
    }

    private boolean isSuperAdmin() { return "SUPER_ADMIN".equals(SecurityUtil.getCurrentUserRole()); }

    private void requireStatus(MemberEntitlementEntity item, String status, String message) {
        if (!status.equals(item.getStatus())) throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, message);
    }

    private String safeNote(NoteRequest request, String fallback) {
        return request == null || request.getNote() == null || request.getNote().isBlank() ? fallback : request.getNote();
    }

    private String number(String prefix) {
        return prefix + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String mask(String token) {
        return token.length() <= 8 ? token : token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}
