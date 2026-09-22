package com.gym.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.commercial.entity.PaymentTransactionEntity;
import com.gym.fitness.module.commercial.entity.SalesOrderEntity;
import com.gym.fitness.module.commercial.repository.MembershipProductRepository;
import com.gym.fitness.module.commercial.repository.MemberEntitlementRepository;
import com.gym.fitness.module.commercial.repository.PaymentTransactionRepository;
import com.gym.fitness.module.commercial.repository.SalesOrderRepository;
import com.gym.fitness.module.finance.entity.EntitlementValueAccountEntity;
import com.gym.fitness.module.finance.entity.EntitlementValueEventEntity;
import com.gym.fitness.module.finance.entity.RefundAllocationEntity;
import com.gym.fitness.module.finance.entity.RefundApplicationEntity;
import com.gym.fitness.module.finance.repository.EntitlementValueAccountRepository;
import com.gym.fitness.module.finance.repository.EntitlementValueEventRepository;
import com.gym.fitness.module.finance.repository.RefundAllocationRepository;
import com.gym.fitness.module.finance.repository.RefundApplicationRepository;
import com.gym.fitness.module.finance.repository.EntitlementFundingRepository;
import com.gym.fitness.module.finance.repository.EntitlementTermPeriodRepository;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:finance_report_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE",
                "spring.jpa.hibernate.ddl-auto=create-drop", "app.ai.enabled=false"})
class FinanceReportIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired SysUserRepository users;
    @Autowired VenueRepository venues;
    @Autowired MemberRepository members;
    @Autowired MembershipProductRepository products;
    @Autowired MemberEntitlementRepository entitlements;
    @Autowired SalesOrderRepository orders;
    @Autowired PaymentTransactionRepository payments;
    @Autowired RefundApplicationRepository refunds;
    @Autowired RefundAllocationRepository allocations;
    @Autowired EntitlementValueAccountRepository accounts;
    @Autowired EntitlementValueEventRepository events;
    @Autowired EntitlementFundingRepository funding;
    @Autowired EntitlementTermPeriodRepository periods;
    @Autowired JwtTokenProvider jwt;
    private Long venueOne;
    private Long venueTwo;
    private Long memberId;
    private Long productId;

    @BeforeEach
    void fixture() {
        venueOne = users.findByUsername("manager").orElseThrow().getVenueId();
        venueTwo = venues.findAll().stream().map(v -> v.getId()).filter(id -> !id.equals(venueOne)).findFirst().orElseThrow();
        memberId = members.findByUserId(users.findByUsername("member").orElseThrow().getId()).orElseThrow().getId();
        productId = products.findAll().stream().filter(p -> p.getVenueId().equals(venueOne)).findFirst().orElseThrow().getId();
    }

    @Test
    void reportUsesTransactionDateKeepsOtherVenueOutAndEscapesCsvFormula() throws Exception {
        SalesOrderEntity first = order(venueOne, "100.00");
        payment(first, "PAYMENT", "100.00", "=HYPERLINK(\"https://unsafe\")", LocalDateTime.of(2024, 8, 31, 10, 0));
        PaymentTransactionEntity refundPayment = payment(first, "REFUND", "30.00", "CASH", LocalDateTime.of(2024, 9, 2, 10, 0));
        RefundApplicationEntity refund = refund(first, 3000);
        RefundAllocationEntity allocation = new RefundAllocationEntity();
        allocation.setRefundId(refund.getId()); allocation.setFundingId(1L); allocation.setOrderId(first.getId()); allocation.setAmountCents(3000); allocation.setPaymentTransactionId(refundPayment.getId()); allocations.save(allocation);
        SalesOrderEntity otherVenue = order(venueTwo, "50.00");
        payment(otherVenue, "PAYMENT", "50.00", "CASH", LocalDateTime.of(2024, 9, 3, 10, 0));
        account(first.getId(), venueOne, 7000, "VERIFIED", 10000, -3000);

        JsonNode report = ok("/api/v1/finance/report?from=2024-09-01&to=2024-09-30", "manager");
        assertEquals("0.00", report.path("receipts").decimalValue().setScale(2).toPlainString());
        assertEquals("30.00", report.path("refunds").decimalValue().setScale(2).toPlainString());
        assertEquals("-30.00", report.path("netCash").decimalValue().setScale(2).toPlainString());
        assertEquals("70.00", report.path("unusedVerifiedValue").decimalValue().setScale(2).toPlainString());
        assertTrue(report.path("unverifiedEntitlements").asInt() > 0, "missing accounts must be reported as unknown, not zero");
        assertTrue(report.path("differences").size() > 0, "missing account rows must remain visible as reconciliation differences");
        String csv = raw("/api/v1/finance/report/export?from=2024-08-01&to=2024-08-31", "manager");
        assertTrue(csv.contains("'=HYPERLINK"), csv);
    }

    @Test
    void reportRejectsUnauthorizedAndInvalidRangesWithoutWriting() throws Exception {
        long before = events.count();
        assertNotEquals(0, call("/api/v1/finance/report?from=2024-09-30&to=2024-09-01", "manager").path("code").asInt());
        assertNotEquals(0, call("/api/v1/finance/report?from=2023-01-01&to=2024-12-31", "manager").path("code").asInt());
        assertNotEquals(0, call("/api/v1/finance/report?from=2024-09-01&to=2024-09-30", "coach").path("code").asInt());
        assertNotEquals(0, call("/api/v1/finance/report?from=2024-09-01&to=2024-09-30", "member").path("code").asInt());
        assertEquals(before, events.count(), "a report request must not append or repair ledger events");
    }

    @Test
    void reportReconcilesHistoricalOrdersFundingAndEveryPaidRefundWithoutRepairing() throws Exception {
        SalesOrderEntity missingReceipt = order(venueOne, "100.00");
        SalesOrderEntity otherOrder = order(venueOne, "10.00");
        PaymentTransactionEntity badRefundPayment = payment(missingReceipt, "REFUND", "10.00", "CASH", LocalDateTime.of(2024, 8, 2, 10, 0));
        RefundApplicationEntity paid = refund(missingReceipt, 1000);
        RefundAllocationEntity badAllocation = new RefundAllocationEntity();
        badAllocation.setRefundId(paid.getId()); badAllocation.setFundingId(999999L); badAllocation.setOrderId(otherOrder.getId());
        badAllocation.setAmountCents(1000); badAllocation.setPaymentTransactionId(badRefundPayment.getId()); allocations.save(badAllocation);
        account(missingReceipt.getId(), venueOne, 10000, "VERIFIED", 10000);
        var source = new com.gym.fitness.module.finance.entity.EntitlementFundingEntity();
        source.setEntitlementId(missingReceipt.getId()); source.setOrderId(missingReceipt.getId()); source.setVenueId(venueOne); source.setReceivedCents(10000); source.setRemainingCents(9000); source.setProductType("TIMES_CARD"); source.setVerified(true); funding.save(source);
        JsonNode report = ok("/api/v1/finance/report?from=2024-08-01&to=2024-08-31", "manager");
        assertTrue(hasDifference(report, "ORDER_PAYMENTS"), report.toString());
        assertTrue(hasDifference(report, "FUNDING_ACCOUNT"), report.toString());
        assertTrue(hasDifference(report, "PAID_REFUND"), report.toString());
        assertEquals(1, events.findByEntitlementIdOrderByIdAsc(missingReceipt.getId()).size(), "reconciliation must not repair ledger data");
    }

    @Test
    void reportFlagsPaidRefundWhoseEntitlementBelongsToAnotherVenue() throws Exception {
        SalesOrderEntity sale = order(venueOne, "10.00");
        PaymentTransactionEntity refundPayment = payment(sale, "REFUND", "10.00", "CASH", LocalDateTime.of(2023, 11, 2, 10, 0));
        var foreign = new com.gym.fitness.module.commercial.entity.MemberEntitlementEntity();
        foreign.setEntitlementNo("REPORT-FOREIGN-" + UUID.randomUUID().toString().replace("-", "")); foreign.setMemberId(memberId); foreign.setProductId(productId); foreign.setSourceOrderId(sale.getId()); foreign.setVenueId(venueTwo);
        foreign.setValidFrom(LocalDate.of(2024, 8, 1)); foreign.setValidUntil(LocalDate.of(2024, 8, 31)); foreign.setTotalUses(1); foreign.setRemainingUses(1); foreign.setStatus("ACTIVE"); entitlements.save(foreign);
        Long foreignEntitlement = foreign.getId();
        RefundApplicationEntity paid = refund(sale, 1000); paid.setEntitlementId(foreignEntitlement); refunds.save(paid);
        var source = new com.gym.fitness.module.finance.entity.EntitlementFundingEntity();
        source.setEntitlementId(foreignEntitlement); source.setOrderId(sale.getId()); source.setVenueId(venueOne); source.setReceivedCents(1000); source.setRemainingCents(0); source.setProductType("TIMES_CARD"); source.setVerified(true); funding.save(source);
        RefundAllocationEntity allocation = new RefundAllocationEntity();
        allocation.setRefundId(paid.getId()); allocation.setFundingId(source.getId()); allocation.setOrderId(sale.getId()); allocation.setAmountCents(1000); allocation.setPaymentTransactionId(refundPayment.getId()); allocations.save(allocation);
        JsonNode report = ok("/api/v1/finance/report?from=2024-08-01&to=2024-08-31", "manager");
        assertTrue(hasDifference(report, "PAID_REFUND"), report.toString());
        assertTrue(java.util.stream.StreamSupport.stream(report.path("differences").spliterator(),false).filter(d->d.path("refundId").asLong()==paid.getId()).allMatch(d->d.path("entitlementId").isNull()),"foreign entitlement ID must not escape via refund trace");
        assertTrue(java.util.stream.StreamSupport.stream(report.path("differences").spliterator(),false).anyMatch(d->d.path("refundId").asLong()==paid.getId()),"scoped refund itself remains traceable");
        assertTrue(raw("/api/v1/finance/report/export?from=2023-11-01&to=2023-11-30","manager").contains("LINK_INCONSISTENT"),"foreign entitlement must not be labeled LINKED in CSV");
    }

    @Test
    void reportFlagsSingleRefundPaymentReusedByTwoPaidRefunds() throws Exception {
        SalesOrderEntity sale = order(venueOne, "10.00");
        PaymentTransactionEntity refundPayment = payment(sale, "REFUND", "10.00", "CASH", LocalDateTime.of(2024, 8, 2, 10, 0));
        Long entitlementId = entitlements.findByVenueIdOrderByIdDesc(venueOne).get(0).getId();
        RefundApplicationEntity first = refund(sale, 1000); first.setEntitlementId(entitlementId); refunds.save(first);
        RefundApplicationEntity second = refund(sale, 1000); second.setEntitlementId(entitlementId); refunds.save(second);
        var source = new com.gym.fitness.module.finance.entity.EntitlementFundingEntity();
        source.setEntitlementId(entitlementId); source.setOrderId(sale.getId()); source.setVenueId(venueOne); source.setReceivedCents(1000); source.setRemainingCents(0); source.setProductType("TIMES_CARD"); source.setVerified(true); funding.save(source);
        for (RefundApplicationEntity paid : List.of(first, second)) {
            RefundAllocationEntity allocation = new RefundAllocationEntity();
            allocation.setRefundId(paid.getId()); allocation.setFundingId(source.getId()); allocation.setOrderId(sale.getId()); allocation.setAmountCents(1000); allocation.setPaymentTransactionId(refundPayment.getId()); allocations.save(allocation);
        }
        JsonNode report = ok("/api/v1/finance/report?from=2024-08-01&to=2024-08-31", "manager");
        assertTrue(hasDifference(report, "PAID_REFUND"), report.toString());
    }

    @Test
    void exportRejects10001RawSuccessfulTransactionsEvenWhenOneRefundIsMalformed() throws Exception {
        SalesOrderEntity sale = order(venueOne, "1.00");
        List<PaymentTransactionEntity> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 10000; i++) rows.add(transaction(sale, "PAYMENT", "1.00", "CASH", LocalDateTime.of(2024, 8, 3, 10, 0).plusSeconds(i)));
        rows.add(transaction(sale, "REFUND", "1.00", "CASH", LocalDateTime.of(2024, 8, 4, 10, 0)));
        payments.saveAll(rows);
        HttpResponse<String> response = exportResponse("/api/v1/finance/report/export?from=2024-08-01&to=2024-08-31", "manager");
        assertFalse(response.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"), response.body());
    }

    @Test
    void reportForecastsElapsedVerifiedTermDayWithoutWritingTimeConsumeEvent() throws Exception {
        String reportPath = "/api/v1/finance/report?from=" + LocalDate.now().minusDays(1) + "&to=" + LocalDate.now();
        BigDecimal previousUnused = ok(reportPath, "manager").path("unusedVerifiedValue").decimalValue();
        var product = new com.gym.fitness.module.commercial.entity.MembershipProductEntity();
        product.setName("报告预估月卡" + UUID.randomUUID()); product.setCategory("CARD"); product.setProductType("MONTHLY_CARD"); product.setValidityDays(3); product.setPrice(new BigDecimal("10.00")); product.setAccessAllowed(true); product.setStatus("ACTIVE"); product.setVenueId(venueOne); products.save(product);
        long orderId = postOk("/api/v1/commercial/orders", "admin", java.util.Map.of("memberId", memberId, "productId", product.getId(), "paidAmount", 10, "paymentMethod", "CASH", "idempotencyKey", java.util.UUID.randomUUID().toString())).path("id").asLong();
        long entitlementId = entitlements.findByMemberIdOrderByIdDesc(memberId).get(0).getId();
        var period = periods.findByEntitlementIdOrderByIdAsc(entitlementId).get(0);
        period.setStartDate(LocalDate.now().minusDays(1)); period.setNextServiceDate(LocalDate.now().minusDays(1)); periods.save(period);
        long eventsBefore = events.findByEntitlementIdOrderByIdAsc(entitlementId).size();
        JsonNode report = ok("/api/v1/finance/report?from=" + LocalDate.now().minusDays(1) + "&to=" + LocalDate.now(), "manager");
        assertEquals("3.33", report.path("serviceConsumed").decimalValue().setScale(2).toPlainString());
        assertEquals(new BigDecimal("6.67"), report.path("unusedVerifiedValue").decimalValue().subtract(previousUnused).setScale(2));
        assertEquals(report.path("unusedVerifiedValue"), ok("/api/v1/finance/report?from=2020-01-01&to=2020-01-01", "manager").path("unusedVerifiedValue"), "valuation must include elapsed days outside cash range");
        assertEquals(eventsBefore, events.findByEntitlementIdOrderByIdAsc(entitlementId).size());
        assertEquals(0, events.findByEntitlementIdOrderByIdAsc(entitlementId).stream().filter(e -> "TIME_CONSUME".equals(e.getEventType())).count());
        assertEquals(orderId, funding.findByEntitlementIdOrderByOrderIdAsc(entitlementId).get(0).getOrderId());
        ok("/api/v1/finance/entitlements/" + entitlementId + "/quote", "manager");
        assertEquals(report.path("unusedVerifiedValue"), ok(reportPath, "manager").path("unusedVerifiedValue"));
        assertEquals(report.path("serviceConsumed"), ok(reportPath, "manager").path("serviceConsumed"));
    }

    @Test
    void malformedLinkedCashIsExportedWithExplicitIntegrityAndNoForeignTrace() throws Exception {
        var sale=order(venueOne,"100.00");var wrong=order(venueOne,"100.00");
        var tx=payment(sale,"REFUND","30.00","BROKEN",LocalDateTime.of(2023,2,2,10,0));
        var paid=refund(sale,3000);
        var allocation=new RefundAllocationEntity();allocation.setRefundId(paid.getId());allocation.setFundingId(999999L);allocation.setOrderId(wrong.getId());allocation.setAmountCents(3000);allocation.setPaymentTransactionId(tx.getId());allocations.save(allocation);
        var report=ok("/api/v1/finance/report?from=2023-02-01&to=2023-02-28","manager");
        assertEquals(new BigDecimal("30.00"),report.path("refunds").decimalValue().setScale(2));
        assertTrue(java.util.stream.StreamSupport.stream(report.path("differences").spliterator(),false).anyMatch(d->d.path("paymentTransactionId").asLong()==tx.getId()&&d.path("message").asText().contains("LINK_INCONSISTENT")));
        assertTrue(raw("/api/v1/finance/report/export?from=2023-02-01&to=2023-02-28","manager").contains("LINK_INCONSISTENT"));
    }

    @Test
    void unusedValuationKeepsPausedAndFutureValueButExhaustsExpiredTermsReadOnly() throws Exception {
        String path="/api/v1/finance/report?from=2019-01-01&to=2019-01-01";
        var before=ok(path,"manager").path("unusedVerifiedValue").decimalValue();
        var product=new com.gym.fitness.module.commercial.entity.MembershipProductEntity();
        product.setName("Valuation"+UUID.randomUUID());product.setCategory("CARD");product.setProductType("MONTHLY_CARD");product.setValidityDays(3);product.setPrice(new BigDecimal("10.00"));product.setAccessAllowed(true);product.setStatus("ACTIVE");product.setVenueId(venueOne);products.save(product);
        postOk("/api/v1/commercial/orders","admin",java.util.Map.of("memberId",memberId,"productId",product.getId(),"paidAmount",10,"paymentMethod", "CASH", "idempotencyKey", java.util.UUID.randomUUID().toString()));
        var entitlement=entitlements.findByMemberIdOrderByIdDesc(memberId).get(0);
        var period=periods.findByEntitlementIdOrderByIdAsc(entitlement.getId()).get(0);
        var account=accounts.findByEntitlementId(entitlement.getId()).orElseThrow();
        period.setNextServiceDate(LocalDate.now().plusDays(1));period.setStartDate(LocalDate.now().plusDays(1));periods.save(period);
        assertEquals(new BigDecimal("10.00"),ok(path,"manager").path("unusedVerifiedValue").decimalValue().subtract(before).setScale(2));
        period.setNextServiceDate(LocalDate.now().minusDays(5));period.setStartDate(LocalDate.now().minusDays(5));periods.save(period);
        account.setPausedAt(LocalDate.now().minusDays(6));accounts.save(account);
        assertEquals(new BigDecimal("10.00"),ok(path,"manager").path("unusedVerifiedValue").decimalValue().subtract(before).setScale(2));
        account.setPausedAt(null);accounts.save(account);entitlement.setStatus("EXPIRED");entitlement.setValidUntil(LocalDate.now().minusDays(1));entitlements.save(entitlement);
        long count=events.count();
        assertEquals(new BigDecimal("0.00"),ok(path,"manager").path("unusedVerifiedValue").decimalValue().subtract(before).setScale(2));
        assertEquals(count,events.count());assertEquals(1000,accounts.findByEntitlementId(entitlement.getId()).orElseThrow().getBalanceCents());assertEquals(0,periods.findById(period.getId()).orElseThrow().getConsumedDays());
        ok("/api/v1/finance/entitlements/"+entitlement.getId()+"/quote","manager");
        assertEquals(new BigDecimal("0.00"),ok(path,"manager").path("unusedVerifiedValue").decimalValue().subtract(before).setScale(2));
    }

    @Test
    void differenceSubjectsIdentifyDistinctOrdersAndPayments() throws Exception {
        var first=order(venueOne,"100.00"); var second=order(venueOne,"100.00");
        var one=payment(first,"REFUND","10.00","TRACE",LocalDateTime.of(2020,2,2,10,0));
        var two=payment(second,"REFUND","20.00","TRACE",LocalDateTime.of(2020,2,2,10,0));
        var report=ok("/api/v1/finance/report?from=2020-02-01&to=2020-02-28","manager");
        for(var sale:List.of(first,second)) assertTrue(java.util.stream.StreamSupport.stream(report.path("differences").spliterator(),false).anyMatch(d->d.path("type").asText().equals("ORDER_PAYMENTS")&&d.path("subjectId").asLong()==sale.getId()),"each order mismatch needs its real order ID");
        for(var tx:List.of(one,two)) assertTrue(java.util.stream.StreamSupport.stream(report.path("differences").spliterator(),false).anyMatch(d->d.path("type").asText().equals("REFUND_ALLOCATION")&&d.path("paymentTransactionId").asLong()==tx.getId()),"each payment mismatch needs its real transaction ID");
    }

    @Test
    void legacyCashAndEveryDifferenceRemainTraceableWithoutAllocations() throws Exception {
        var first = order(venueOne, "100.00"); var second = order(venueOne, "100.00");
        var foreign = order(venueTwo, "100.00");
        var one = payment(first, "REFUND", "30.00", "LEGACY", LocalDateTime.of(2021, 2, 2, 10, 0));
        var two = payment(second, "REFUND", "20.00", "LEGACY", LocalDateTime.of(2021, 2, 3, 10, 0));
        var excluded = payment(foreign, "REFUND", "90.00", "LEGACY", LocalDateTime.of(2021, 2, 3, 10, 0));
        payment(first, "PAYMENT", "100.00", "LEGACY", LocalDateTime.of(2021, 2, 1, 10, 0));
        long eventCount = events.count(), allocationCount = allocations.count();
        var report = ok("/api/v1/finance/report?from=2021-02-01&to=2021-02-28", "reception");
        assertEquals(new BigDecimal("50.00"), report.path("refunds").decimalValue().setScale(2));
        assertEquals(new BigDecimal("50.00"), report.path("netCash").decimalValue().setScale(2));
        assertEquals(new BigDecimal("50.00"), report.path("channels").get(0).path("refunds").decimalValue().setScale(2));
        for (var sale : List.of(first, second)) assertTrue(java.util.stream.StreamSupport.stream(report.path("differences").spliterator(), false).anyMatch(d -> d.path("type").asText().equals("ORDER_PAYMENTS") && d.path("subjectType").asText().equals("ORDER") && d.path("subjectId").asLong() == sale.getId() && d.path("orderId").asLong() == sale.getId()));
        for (var tx : List.of(one, two)) assertTrue(java.util.stream.StreamSupport.stream(report.path("differences").spliterator(), false).anyMatch(d -> d.path("type").asText().equals("REFUND_ALLOCATION") && d.path("subjectType").asText().equals("PAYMENT_TRANSACTION") && d.path("subjectId").asLong() == tx.getId() && d.path("paymentTransactionId").asLong() == tx.getId()));
        assertFalse(java.util.stream.StreamSupport.stream(report.path("differences").spliterator(), false).anyMatch(d -> d.path("paymentTransactionId").asLong() == excluded.getId()));
        String csv = raw("/api/v1/finance/report/export?from=2021-02-01&to=2021-02-28", "reception");
        assertTrue(csv.contains(one.getTransactionNo()) && csv.contains(two.getTransactionNo()) && csv.contains("LEGACY_OR_UNLINKED"), csv);
        assertEquals(eventCount, events.count()); assertEquals(allocationCount, allocations.count());
    }

    @Test
    void validLargeBoxedIdsHaveNoFalseRefundDifferenceAndExportCash() throws Exception {
        SalesOrderEntity sale;
        do { sale = order(venueOne, "10.00"); } while (sale.getId() <= 127);
        com.gym.fitness.module.commercial.entity.MemberEntitlementEntity entitlement;
        do {
            entitlement = new com.gym.fitness.module.commercial.entity.MemberEntitlementEntity();
            entitlement.setEntitlementNo("LARGE-" + UUID.randomUUID()); entitlement.setMemberId(memberId); entitlement.setProductId(productId); entitlement.setSourceOrderId(sale.getId()); entitlement.setVenueId(venueOne); entitlement.setValidFrom(LocalDate.now()); entitlement.setValidUntil(LocalDate.now().plusDays(3)); entitlement.setTotalUses(1); entitlement.setRemainingUses(0); entitlement.setStatus("REFUNDED"); entitlements.save(entitlement);
        } while (entitlement.getId() <= 127);
        sale.setRefundedAmount(new BigDecimal("10.00"));sale.setStatus("REFUNDED");orders.save(sale);
        payment(sale,"PAYMENT","10.00","LARGE",LocalDateTime.of(2022,2,1,10,0));
        account(entitlement.getId(),venueOne,0,"VERIFIED",1000,-1000);
        var tx = payment(sale, "REFUND", "10.00", "LARGE", LocalDateTime.of(2022, 2, 2, 10, 0));
        var paid = refund(sale, 1000); paid.setEntitlementId(entitlement.getId()); refunds.save(paid);
        var source = new com.gym.fitness.module.finance.entity.EntitlementFundingEntity(); source.setEntitlementId(entitlement.getId()); source.setOrderId(sale.getId()); source.setVenueId(venueOne); source.setReceivedCents(1000); source.setRemainingCents(0); source.setProductType("TIMES_CARD"); source.setVerified(true); funding.save(source);
        var allocation = new RefundAllocationEntity(); allocation.setRefundId(paid.getId()); allocation.setFundingId(source.getId()); allocation.setOrderId(sale.getId()); allocation.setAmountCents(1000); allocation.setPaymentTransactionId(tx.getId()); allocations.save(allocation);
        var report = ok("/api/v1/finance/report?from=2022-02-01&to=2022-02-28", "manager");
        assertEquals(new BigDecimal("10.00"), report.path("refunds").decimalValue().setScale(2));
        assertEquals(new BigDecimal("10.00"), report.path("receipts").decimalValue().setScale(2));
        assertEquals(new BigDecimal("0.00"), report.path("netCash").decimalValue().setScale(2));
        assertFalse(java.util.stream.StreamSupport.stream(report.path("differences").spliterator(), false).anyMatch(d -> (d.path("type").asText().equals("PAID_REFUND") && d.path("refundId").asLong() == paid.getId()) || (d.path("type").asText().equals("REFUND_ALLOCATION") && d.path("paymentTransactionId").asLong() == tx.getId())), report.toString());
        String csv=raw("/api/v1/finance/report/export?from=2022-02-01&to=2022-02-28", "manager");
        assertTrue(csv.contains(tx.getTransactionNo())&&csv.contains("\"LINKED\""),csv);
        assertFalse(csv.contains("LINK_INCONSISTENT"),csv);
    }

    private SalesOrderEntity order(Long venueId, String amount) {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setOrderNo("REPORT-" + UUID.randomUUID()); order.setOrderType("NEW"); order.setMemberId(memberId);
        order.setProductId(productId); order.setVenueId(venueId); order.setOriginalAmount(new BigDecimal(amount));
        order.setPaidAmount(new BigDecimal(amount)); order.setRefundedAmount(BigDecimal.ZERO); order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.of(2024, 8, 31, 10, 0)); return orders.save(order);
    }

    private PaymentTransactionEntity payment(SalesOrderEntity order, String type, String amount, String method, LocalDateTime when) {
        return payments.save(transaction(order, type, amount, method, when));
    }
    private PaymentTransactionEntity transaction(SalesOrderEntity order, String type, String amount, String method, LocalDateTime when) {
        PaymentTransactionEntity payment = new PaymentTransactionEntity();
        payment.setTransactionNo("REPORT-TX-" + UUID.randomUUID()); payment.setOrderId(order.getId()); payment.setTransactionType(type);
        payment.setAmount(new BigDecimal(amount)); payment.setPaymentMethod(method); payment.setStatus("SUCCESS"); payment.setOccurredAt(when);
        return payment;
    }

    private RefundApplicationEntity refund(SalesOrderEntity order, long cents) {
        RefundApplicationEntity refund = new RefundApplicationEntity();
        refund.setRefundNo("REPORT-RF-" + UUID.randomUUID()); refund.setEntitlementId(order.getId()); refund.setMemberId(memberId);
        refund.setVenueId(order.getVenueId()); refund.setStatus("PAID"); refund.setAmountCents(cents); refund.setValueVersion(1);
        refund.setReason("report fixture"); refund.setRequestedBy(users.findByUsername("manager").orElseThrow().getId());
        refund.setIdempotencyKey(UUID.randomUUID().toString()); refund.setRequestFingerprint(UUID.randomUUID().toString()); return refunds.save(refund);
    }

    private void account(Long entitlementId, Long venueId, long balance, String status, long... deltas) {
        EntitlementValueAccountEntity account = new EntitlementValueAccountEntity();
        account.setEntitlementId(entitlementId); account.setMemberId(memberId); account.setVenueId(venueId); account.setVerificationStatus(status); account.setBalanceCents(balance); accounts.save(account);
        long running = 0;
        for (int i = 0; i < deltas.length; i++) {
            running += deltas[i]; EntitlementValueEventEntity event = new EntitlementValueEventEntity();
            event.setEntitlementId(entitlementId); event.setVenueId(venueId); event.setBusinessKey("REPORT-EVENT-" + UUID.randomUUID());
            event.setEventType(i == 0 ? "ISSUE" : "REFUND"); event.setDeltaCents(deltas[i]); event.setBalanceAfterCents(running);
            event.setVerified(true); event.setOccurredAt(LocalDateTime.of(2024, 9, 2, 10, 0)); events.save(event);
        }
    }

    private JsonNode ok(String path, String username) throws Exception {
        JsonNode response = call(path, username); assertEquals(0, response.path("code").asInt(), response.toString()); return response.path("data");
    }
    private JsonNode call(String path, String username) throws Exception {
        var user = users.findByUsername(username).orElseThrow();
        String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRole().getCode(), user.getVenueId());
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + token).GET().build();
        return json.readTree(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body());
    }
    private JsonNode postOk(String path, String username, Object body) throws Exception {
        var user = users.findByUsername(username).orElseThrow();
        String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRole().getCode(), user.getVenueId());
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        JsonNode response = json.readTree(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()); assertEquals(0, response.path("code").asInt(), response.toString()); return response.path("data");
    }
    private String raw(String path, String username) throws Exception {
        HttpResponse<String> response = exportResponse(path, username);
        assertEquals(200, response.statusCode(), response.body()); return response.body();
    }
    private HttpResponse<String> exportResponse(String path, String username) throws Exception {
        var user = users.findByUsername(username).orElseThrow();
        String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRole().getCode(), user.getVenueId());
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + token).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
    private boolean hasDifference(JsonNode report, String type) { for (JsonNode difference : report.path("differences")) if (type.equals(difference.path("type").asText())) return true; return false; }
}
