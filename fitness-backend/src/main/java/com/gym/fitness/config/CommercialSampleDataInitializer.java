package com.gym.fitness.config;

import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.commercial.entity.*;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.entity.VenueEntity;
import com.gym.fitness.module.organization.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@Profile("!prod | sample")
@Order(100)
@RequiredArgsConstructor
public class CommercialSampleDataInitializer implements ApplicationRunner {
    private final VenueRepository venues;
    private final SysUserRepository users;
    private final MemberRepository members;
    private final MembershipProductRepository products;
    private final SalesOrderRepository orders;
    private final MemberEntitlementRepository entitlements;
    private final EntitlementOperationRepository operations;
    private final PaymentTransactionRepository payments;
    private final BusinessDocumentRepository documents;
    private final AccessRecordRepository accessRecords;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SysUserEntity admin = users.findByUsername("admin").orElse(null);
        if (admin == null || members.count() == 0) return;
        List<VenueEntity> venueList = venues.findAllByOrderByIdAsc();
        Map<Long, List<MembershipProductEntity>> productMap = new HashMap<>();
        for (VenueEntity venue : venueList) productMap.put(venue.getId(), seedProducts(venue, admin));

        List<MemberEntity> memberList = members.findAll();
        memberList.forEach(member -> {
            if (member.getAccessToken() == null) {
                member.setAccessToken(UUID.randomUUID().toString().replace("-", ""));
                members.save(member);
            }
        });
        int seeded = 0;
        for (MemberEntity member : memberList) {
            if (!"ACTIVE".equals(member.getStatus()) || Boolean.TRUE.equals(member.getBlacklistFlag())) continue;
            if (!entitlements.findByMemberIdOrderByIdDesc(member.getId()).isEmpty()) continue;
            List<MembershipProductEntity> available = productMap.get(member.getVenueId());
            if (available == null || available.isEmpty()) continue;
            MembershipProductEntity product = member.getUserId() != null
                    ? available.stream().filter(item -> "YEAR_CARD".equals(item.getProductType())).findFirst().orElse(available.get(0))
                    : available.get(seeded % available.size());
            createSale(member, product, admin, seeded);
            seeded++;
        }
        if (accessRecords.count() == 0) seedAccessRecords(memberList, admin);
        log.info("Commercial sample data ready: {} products, {} orders, {} entitlements",
                products.count(), orders.count(), entitlements.count());
    }

    private List<MembershipProductEntity> seedProducts(VenueEntity venue, SysUserEntity admin) {
        List<ProductSeed> seeds = List.of(
                new ProductSeed("活力月卡", "CARD", "MONTH_CARD", 30, null, true, "399", "适合短期体验，无限次入场"),
                new ProductSeed("进阶季卡", "CARD", "QUARTER_CARD", 90, null, true, "999", "连续训练三个月，无限次入场"),
                new ProductSeed("年度畅练卡", "CARD", "YEAR_CARD", 365, null, true, "2999", "全年无限次入场，适合长期训练"),
                new ProductSeed("健身 20 次卡", "PACKAGE", "VISIT_CARD", 180, 20, true, "1280", "半年有效，每次入场核销一次"),
                new ProductSeed("私教 12 节课包", "PACKAGE", "PRIVATE_PACKAGE", 120, 12, false, "4680", "一对一私教训练，不作为门禁凭证")
        );
        List<MembershipProductEntity> result = new ArrayList<>();
        List<MembershipProductEntity> existing = products.findByVenueIdOrderByIdDesc(venue.getId());
        for (ProductSeed seed : seeds) {
            MembershipProductEntity product = existing.stream().filter(item -> item.getName().equals(seed.name())).findFirst().orElseGet(() -> {
                MembershipProductEntity item = new MembershipProductEntity();
                item.setName(seed.name()); item.setCategory(seed.category()); item.setProductType(seed.type());
                item.setValidityDays(seed.days()); item.setTotalUses(seed.uses()); item.setAccessAllowed(seed.accessAllowed());
                item.setPrice(new BigDecimal(seed.price())); item.setDescription(seed.description());
                item.setVenueId(venue.getId()); item.setStatus("ACTIVE"); item.setCreatedBy(admin.getId());
                return products.save(item);
            });
            result.add(product);
        }
        return result;
    }

    private void createSale(MemberEntity member, MembershipProductEntity product, SysUserEntity admin, int index) {
        String suffix = String.format("%06d", member.getId());
        SalesOrderEntity order = new SalesOrderEntity();
        order.setOrderNo("DEMO-SO-" + suffix); order.setOrderType("NEW"); order.setMemberId(member.getId());
        order.setProductId(product.getId()); order.setVenueId(member.getVenueId());
        order.setOriginalAmount(product.getPrice()); order.setPaidAmount(product.getPrice()); order.setRefundedAmount(BigDecimal.ZERO);
        order.setPaymentMethod(index % 3 == 0 ? "WECHAT" : index % 3 == 1 ? "ALIPAY" : "CARD");
        order.setStatus("PAID"); order.setRemark("商业功能演示订单"); order.setOperatorId(admin.getId());
        order.setPaidAt(LocalDateTime.now().minusDays(index % 15)); order = orders.save(order);

        MemberEntitlementEntity entitlement = new MemberEntitlementEntity();
        entitlement.setEntitlementNo("DEMO-ME-" + suffix); entitlement.setMemberId(member.getId());
        entitlement.setProductId(product.getId()); entitlement.setSourceOrderId(order.getId()); entitlement.setVenueId(member.getVenueId());
        entitlement.setValidFrom(LocalDate.now().minusDays(index * 3L));
        entitlement.setValidUntil(entitlement.getValidFrom().plusDays(product.getValidityDays() - 1L));
        entitlement.setTotalUses(product.getTotalUses());
        entitlement.setRemainingUses(product.getTotalUses() == null ? null : Math.max(2, product.getTotalUses() - index));
        entitlement.setStatus(index == 7 ? "FROZEN" : "ACTIVE");
        entitlement.setFrozenAt(index == 7 ? LocalDateTime.now().minusDays(2) : null);
        entitlement.setAccumulatedFrozenDays(0); entitlement = entitlements.save(entitlement);

        EntitlementOperationEntity operation = new EntitlementOperationEntity();
        operation.setEntitlementId(entitlement.getId()); operation.setOperationType("ISSUE"); operation.setAfterStatus(entitlement.getStatus());
        operation.setAmount(product.getPrice()); operation.setNote("示例开卡"); operation.setOperatorId(admin.getId()); operations.save(operation);

        PaymentTransactionEntity payment = new PaymentTransactionEntity();
        payment.setTransactionNo("DEMO-TX-" + suffix); payment.setOrderId(order.getId()); payment.setTransactionType("PAYMENT");
        payment.setAmount(product.getPrice()); payment.setPaymentMethod(order.getPaymentMethod()); payment.setStatus("SUCCESS");
        payment.setOperatorId(admin.getId()); payments.save(payment);

        createDocument(order, member, admin, "CONTRACT", "DEMO-CT-" + suffix, "会员服务合同");
        createDocument(order, member, admin, "RECEIPT", "DEMO-RC-" + suffix, "FitChain 收款收据");
    }

    private void createDocument(SalesOrderEntity order, MemberEntity member, SysUserEntity admin,
                                String type, String number, String title) {
        BusinessDocumentEntity document = new BusinessDocumentEntity();
        document.setDocumentNo(number); document.setDocumentType(type); document.setOrderId(order.getId());
        document.setMemberId(member.getId()); document.setTitle(title); document.setAmount(order.getPaidAmount());
        document.setStatus("ISSUED"); document.setIssuedAt(order.getPaidAt()); document.setCreatedBy(admin.getId());
        documents.save(document);
    }

    private void seedAccessRecords(List<MemberEntity> memberList, SysUserEntity admin) {
        int count = 0;
        for (MemberEntity member : memberList) {
            if (count >= 8) break;
            MemberEntitlementEntity entitlement = entitlements.findByMemberIdAndStatusOrderByValidUntilAsc(member.getId(), "ACTIVE")
                    .stream().findFirst().orElse(null);
            if (entitlement == null) continue;
            AccessRecordEntity record = new AccessRecordEntity();
            record.setMemberId(member.getId()); record.setEntitlementId(entitlement.getId()); record.setVenueId(member.getVenueId());
            record.setAccessTokenMasked(member.getAccessToken().substring(0, 4) + "****"); record.setDirection("IN");
            record.setResult("APPROVED"); record.setReason("二维码入场成功"); record.setOperatorId(admin.getId());
            accessRecords.save(record); count++;
        }
    }

    private record ProductSeed(String name, String category, String type, int days, Integer uses,
                               boolean accessAllowed, String price, String description) {}
}
