package com.gym.fitness;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.commercial.entity.*;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:refund_finance_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE","spring.jpa.hibernate.ddl-auto=create-drop","app.ai.enabled=false"})
class RefundFinanceIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgres(org.springframework.test.context.DynamicPropertyRegistry p) {
        String url=System.getenv("FITCHAIN_FINANCE_TEST_DB_URL"); if(url==null)return;
        if(!"jdbc:postgresql://127.0.0.1:15432/fitchain_test_finance_20260917".equals(url))throw new IllegalArgumentException("Only the dedicated disposable finance database may run destructive schema tests");
        p.add("spring.datasource.url",()->url);p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");p.add("spring.datasource.username",()->"fitchain_finance_test");p.add("spring.datasource.password",()->System.getenv("FITCHAIN_TEST_DB_PASSWORD"));
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired SysUserRepository users;
    @Autowired MemberRepository members;
    @Autowired MembershipProductRepository products;
    @Autowired MemberEntitlementRepository entitlements;
    @Autowired SalesOrderRepository orders;
    @Autowired PaymentTransactionRepository payments;
    @Autowired JwtTokenProvider jwt;
    @Autowired com.gym.fitness.module.finance.repository.EntitlementValueAccountRepository valueAccounts;
    @Autowired com.gym.fitness.module.finance.repository.EntitlementFundingRepository funding;
    @Autowired com.gym.fitness.module.finance.repository.EntitlementValueEventRepository valueEvents;
    @Autowired com.gym.fitness.module.finance.repository.EntitlementTermPeriodRepository periods;
    @Autowired com.gym.fitness.module.course.repository.CourseRepository courses;
    @Autowired com.gym.fitness.module.course.repository.CourseScheduleRepository schedules;
    @Autowired com.gym.fitness.module.booking.repository.BookingRepository bookings;
    @Autowired com.gym.fitness.module.organization.repository.VenueRepository venues;
    @Autowired com.gym.fitness.module.commercial.repository.AccessGrantRepository accessGrants;
    Long memberId,venueId,entitlementId,orderId; String accessToken;
    @BeforeEach void fixture() throws Exception {
        venueId=users.findByUsername("manager").orElseThrow().getVenueId();
        Long demoMember=members.findByUserId(users.findByUsername("member").orElseThrow().getId()).orElseThrow().getId();
        // Seeded future bookings are unrelated to these isolated finance scenarios.
        for(var b:bookings.findByMemberIdOrderByBookedAtDesc(demoMember))if(Set.of("BOOKED","WAITLISTED").contains(b.getStatus())){b.setStatus("CANCELLED");bookings.save(b);}
        var m=new MemberEntity();m.setMemberNo(UUID.randomUUID().toString());m.setRealName("退款测试");m.setPhone("13900000000");m.setVenueId(venueId);m.setStatus("ACTIVE");m.setBlacklistFlag(false);m.setAccessToken(UUID.randomUUID().toString());members.save(m);memberId=m.getId();accessToken=m.getAccessToken();
        var p=new MembershipProductEntity();p.setName("精确次卡"+UUID.randomUUID());p.setCategory("PACKAGE");p.setProductType("TIMES_CARD");p.setValidityDays(90);p.setTotalUses(3);p.setPrice(new BigDecimal("10.00"));p.setAccessAllowed(true);p.setStatus("ACTIVE");p.setVenueId(venueId);products.save(p);
        var sale=ok("POST","/api/v1/commercial/orders","admin",Map.of("memberId",memberId,"productId",p.getId(),"paidAmount",10,"paymentMethod", "CASH", "idempotencyKey", java.util.UUID.randomUUID().toString()));orderId=sale.path("id").asLong();entitlementId=entitlements.findByMemberIdOrderByIdDesc(memberId).get(0).getId();
    }
    HttpResponse<String> response(String method,String path,String who,Object body)throws Exception {
        var u=users.findByUsername(who).orElseThrow();String token=jwt.generateToken(u.getId(),u.getUsername(),u.getRole().getCode(),u.getVenueId());
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Bearer "+token).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
    }
    /** 门禁二维码已改为 60 秒一次性令牌，所以每次核销前都要新签发一个。 */
    String freshToken(){
        var g=new com.gym.fitness.module.commercial.entity.AccessGrantEntity();
        g.setMemberId(memberId);g.setVenueId(venueId);
        g.setToken(UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));
        g.setIssuedAt(LocalDateTime.now());g.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return accessGrants.save(g).getToken();
    }
    JsonNode call(String method,String path,String who,Object body)throws Exception {return json.readTree(response(method,path,who,body).body());}
    JsonNode ok(String method,String path,String who,Object body)throws Exception {var r=call(method,path,who,body);assertTrue(r.has("code"),r.toString());assertEquals(0,r.path("code").asInt(),r.toString());return r.path("data");}
    JsonNode quote()throws Exception{return ok("GET","/api/v1/finance/entitlements/"+entitlementId+"/quote","admin",null);}
    @Test void oldRefundCannotBypassApproval()throws Exception {
        var r=call("POST","/api/v1/commercial/entitlements/"+entitlementId+"/refund","admin",Map.of("amount",10,"paymentMethod","CASH","reason","退剩余"));
        assertNotEquals(0,r.path("code").asInt(),"Old direct refund must be rejected");assertEquals(new BigDecimal("0.00"),orders.findById(orderId).orElseThrow().getRefundedAmount().setScale(2));
    }
    @Test void newSaleHasExactVerifiedBalanceAndThreeScansExhaustIt()throws Exception {
        assertEquals("VERIFIED",quote().path("verificationStatus").asText());assertEquals(new BigDecimal("10.00"),quote().path("refundableAmount").decimalValue().setScale(2));
        String[] balances={"6.67","3.33","0.00"};
        for(String balance:balances){assertEquals("APPROVED",ok("POST","/api/v1/commercial/access/scan","admin",Map.of("token",freshToken(),"direction","IN")).path("result").asText());assertEquals(new BigDecimal(balance),quote().path("refundableAmount").decimalValue().setScale(2));}
        assertEquals(0,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
    }
    @Test void approvalAndPaymentAreSeparateAndIdempotent()throws Exception {
        var q=quote();var body=Map.of("entitlementId",entitlementId,"version",q.path("version").asLong(),"remainingUses",3,"reason","退剩余","idempotencyKey",UUID.randomUUID().toString());
        var r=ok("POST","/api/v1/finance/refunds","reception",body);long id=r.path("id").asLong();assertEquals("PENDING",r.path("status").asText());assertEquals(id,ok("POST","/api/v1/finance/refunds","reception",body).path("id").asLong());
        assertNotEquals(0,call("POST","/api/v1/commercial/entitlements/"+entitlementId+"/renew","admin",Map.of("additionalUses",1,"amount",5,"paymentMethod","CASH")).path("code").asInt());
        assertNotEquals(0,call("POST","/api/v1/finance/refunds/"+id+"/approve","reception",Map.of("reason","同意")).path("code").asInt());
        assertEquals("APPROVED",ok("POST","/api/v1/finance/refunds/"+id+"/approve","manager",Map.of("reason","核对同意")).path("status").asText());
        assertEquals(new BigDecimal("0.00"),orders.findById(orderId).orElseThrow().getRefundedAmount().setScale(2));
        var pay=Map.of("amount",10,"channel","CASH","externalReference",UUID.randomUUID().toString(),"occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"note","线下退款登记");
        for(int i=0;i<2;i++)assertEquals("PAID",ok("POST","/api/v1/finance/refunds/"+id+"/pay","manager",pay).path("status").asText());
        assertEquals(new BigDecimal("10.00"),orders.findById(orderId).orElseThrow().getRefundedAmount().setScale(2));assertEquals("REFUNDED",entitlements.findById(entitlementId).orElseThrow().getStatus());assertEquals(0,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
        assertEquals(1,payments.findByOrderIdInOrderByOccurredAtDesc(List.of(orderId)).stream().filter(p->"REFUND".equals(p.getTransactionType())).count());
    }
    long request(String who)throws Exception{return ok("POST","/api/v1/finance/refunds",who,Map.of("entitlementId",entitlementId,"version",quote().path("version").asLong(),"reason","未使用服务退款","idempotencyKey",UUID.randomUUID().toString())).path("id").asLong();}
    @Test void multiSourceRenewalOnlyRefundsUnusedValue()throws Exception {
        ok("POST","/api/v1/commercial/access/scan","admin",Map.of("token",freshToken()));
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/renew","admin",Map.of("additionalUses",2,"amount",20,"paymentMethod","CASH"));
        assertEquals(new BigDecimal("26.67"),quote().path("refundableAmount").decimalValue().setScale(2));
        long id=request("reception");ok("POST","/api/v1/finance/refunds/"+id+"/approve","manager",Map.of("reason","来源核验"));
        var r=ok("POST","/api/v1/finance/refunds/"+id+"/pay","manager",Map.of("amount",new BigDecimal("26.67"),"channel","CASH","externalReference",UUID.randomUUID().toString(),"occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"note","实际登记"));
        assertEquals(2,r.path("allocations").size());assertEquals(new BigDecimal("6.67"),orders.findById(orderId).orElseThrow().getRefundedAmount().setScale(2));
    }
    @Test void selfApprovalAndApprovedReceptionCancellationAreRejected()throws Exception {
        long id=request("manager");assertNotEquals(0,call("POST","/api/v1/finance/refunds/"+id+"/approve","manager",Map.of("reason","自审")).path("code").asInt());
        ok("POST","/api/v1/finance/refunds/"+id+"/approve","admin",Map.of("reason","总部核验"));
        assertNotEquals(0,call("POST","/api/v1/finance/refunds/"+id+"/cancel","reception",Map.of("reason","撤回")).path("code").asInt());
        assertEquals("CANCELLED",ok("POST","/api/v1/finance/refunds/"+id+"/cancel","admin",Map.of("reason","尚未线下支付，撤回重核")).path("status").asText());
        assertTrue(quote().path("activeRefundId").isNull());
    }
    @Test void moneyValidationPrecedesOrderWritesAndCoachHasNoFinanceAccess()throws Exception {
        long before=orders.count();
        for(String amount:List.of("0.001","-1.00","10000000000.00"))assertNotEquals(0,call("POST","/api/v1/commercial/entitlements/"+entitlementId+"/renew","admin",Map.of("additionalUses",1,"amount",new BigDecimal(amount),"paymentMethod","CASH")).path("code").asInt());
        assertEquals(before,orders.count());assertNotEquals(0,call("GET","/api/v1/finance/refunds","coach",null).path("code").asInt());assertNotEquals(0,call("GET","/api/v1/finance/entitlements/"+entitlementId+"/quote","member",null).path("code").asInt());
    }
    @Test void concurrentRequestsCreateOneActiveRefund()throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);long version=quote().path("version").asLong();
        try{var futures=new ArrayList<java.util.concurrent.Future<JsonNode>>();for(int i=0;i<2;i++)futures.add(pool.submit(()->{gate.await();return call("POST","/api/v1/finance/refunds","reception",Map.of("entitlementId",entitlementId,"version",version,"reason","并发退款","idempotencyKey",UUID.randomUUID().toString()));}));gate.countDown();int successes=0;for(var f:futures)if(f.get(20,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()==0)successes++;assertEquals(1,successes);}finally{pool.shutdownNow();}
    }
    void legacy() {
        var first=entitlements.findById(entitlementId).orElseThrow();first.setStatus("FROZEN");entitlements.save(first);
        var order=new SalesOrderEntity();order.setOrderNo(UUID.randomUUID().toString());order.setOrderType("NEW");order.setMemberId(memberId);order.setProductId(first.getProductId());order.setVenueId(venueId);order.setOriginalAmount(new BigDecimal("10.00"));order.setPaidAmount(new BigDecimal("10.00"));order.setStatus("PAID");orders.save(order);orderId=order.getId();
        var e=new MemberEntitlementEntity();e.setEntitlementNo(UUID.randomUUID().toString());e.setMemberId(memberId);e.setProductId(first.getProductId());e.setSourceOrderId(orderId);e.setVenueId(venueId);e.setTotalUses(3);e.setRemainingUses(3);e.setValidFrom(LocalDate.now());e.setValidUntil(LocalDate.now().plusDays(90));e.setStatus("ACTIVE");entitlements.save(e);entitlementId=e.getId();
    }
    @Test void legacyIsUnknownAndRenewalIsMergedOnceDuringReconciliation()throws Exception {
        legacy();assertEquals("UNVERIFIED",quote().path("verificationStatus").asText());assertTrue(quote().path("refundableAmount").isNull());
        ok("POST","/api/v1/commercial/access/scan","admin",Map.of("token",freshToken()));assertEquals(2,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/renew","admin",Map.of("additionalUses",2,"amount",20,"paymentMethod","CASH"));assertEquals("UNVERIFIED",quote().path("verificationStatus").asText());
        long renewOrder=funding.findByEntitlementIdOrderByOrderIdAsc(entitlementId).get(0).getOrderId();var q=quote();var reconcile=Map.of("version",q.path("version").asLong(),"remainingUses",4,"reason","凭证和剩余服务核验","sources",List.of(Map.of("orderId",orderId,"unusedAmount",new BigDecimal("6.67"),"productType","TIMES_CARD"),Map.of("orderId",renewOrder,"unusedAmount",20)));
        var result=ok("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",reconcile);assertEquals(new BigDecimal("26.67"),result.path("refundableAmount").decimalValue().setScale(2));assertEquals(2,result.path("funding").size());
        assertEquals(2,funding.findByOrderId(renewOrder).orElseThrow().getTotalUnits(),"Known renewal service quantity is an immutable original snapshot");
        assertNotEquals(0,call("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",reconcile).path("code").asInt());
        assertEquals(1,valueEvents.findByEntitlementIdOrderByIdAsc(entitlementId).stream().filter(v->"UNVERIFIED_CONSUME".equals(v.getEventType())&&!v.isVerified()).count());
    }
    @Test void sharedSourceCannotBeReconciledTwiceAndStaleInventoryIsRejected()throws Exception {
        legacy();var q=quote();var source=List.of(Map.of("orderId",orderId,"unusedAmount",10,"productType","TIMES_CARD"));
        assertNotEquals(0,call("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",Map.of("version",q.path("version").asLong(),"remainingUses",2,"reason","旧预览","sources",source)).path("code").asInt());
        ok("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",Map.of("version",q.path("version").asLong(),"remainingUses",3,"reason","已核实","sources",source));
        var other=entitlements.findById(entitlementId).orElseThrow();other.setId(null);other.setEntitlementNo(UUID.randomUUID().toString());entitlements.save(other);entitlementId=other.getId();
        assertNotEquals(0,call("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",Map.of("version",quote().path("version").asLong(),"remainingUses",3,"reason","不能重复用资金","sources",source)).path("code").asInt());
    }
    void termSale()throws Exception {
        var p=new MembershipProductEntity();p.setName("三天卡"+UUID.randomUUID());p.setCategory("CARD");p.setProductType("MONTHLY_CARD");p.setValidityDays(3);p.setPrice(new BigDecimal("10.00"));p.setAccessAllowed(true);p.setStatus("ACTIVE");p.setVenueId(venueId);products.save(p);
        var sale=ok("POST","/api/v1/commercial/orders","admin",Map.of("memberId",memberId,"productId",p.getId(),"paidAmount",10,"paymentMethod", "CASH", "idempotencyKey", java.util.UUID.randomUUID().toString()));orderId=sale.path("id").asLong();entitlementId=entitlements.findByMemberIdOrderByIdDesc(memberId).get(0).getId();
    }
    @Test void timeServiceUsesPastCompleteDaysAndFreezeShiftsFutureRenewal()throws Exception {
        termSale();assertEquals(3,quote().path("remainingServiceDays").asInt());
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/renew","admin",Map.of("additionalDays",3,"amount",20,"paymentMethod","CASH"));
        for(var p:periods.findByEntitlementIdOrderByIdAsc(entitlementId)){p.setStartDate(p.getStartDate().minusDays(2));p.setNextServiceDate(p.getNextServiceDate().minusDays(2));periods.save(p);}
        var e=entitlements.findById(entitlementId).orElseThrow();e.setValidFrom(e.getValidFrom().minusDays(2));e.setValidUntil(e.getValidUntil().minusDays(2));entitlements.save(e);
        assertEquals(new BigDecimal("23.34"),quote().path("refundableAmount").decimalValue().setScale(2));assertEquals(4,quote().path("remainingServiceDays").asInt());
        var future=periods.findByEntitlementIdOrderByIdAsc(entitlementId).get(1).getNextServiceDate();var until=e.getValidUntil();
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/freeze","manager",Map.of("note","暂停"));
        var a=valueAccounts.findByEntitlementId(entitlementId).orElseThrow();a.setPausedAt(LocalDate.now().minusDays(2));valueAccounts.save(a);
        assertEquals(new BigDecimal("23.34"),quote().path("refundableAmount").decimalValue().setScale(2));
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/unfreeze","manager",Map.of("note","恢复"));
        assertEquals(until.plusDays(2),entitlements.findById(entitlementId).orElseThrow().getValidUntil());assertEquals(future.plusDays(2),periods.findByEntitlementIdOrderByIdAsc(entitlementId).get(1).getNextServiceDate());
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/freeze","manager",Map.of());ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/unfreeze","manager",Map.of());assertEquals(until.plusDays(2),entitlements.findById(entitlementId).orElseThrow().getValidUntil());
    }
    @Test void rejectionRestoresOnlyActualPausedDays()throws Exception {
        termSale();var until=entitlements.findById(entitlementId).orElseThrow().getValidUntil();long id=request("reception");var a=valueAccounts.findByEntitlementId(entitlementId).orElseThrow();a.setPausedAt(LocalDate.now().minusDays(2));valueAccounts.save(a);
        assertEquals(new BigDecimal("10.00"),quote().path("refundableAmount").decimalValue().setScale(2));ok("POST","/api/v1/finance/refunds/"+id+"/reject","manager",Map.of("reason","会员继续服务"));assertEquals(until.plusDays(2),entitlements.findById(entitlementId).orElseThrow().getValidUntil());assertTrue(quote().path("activeRefundId").isNull());
    }
    @Test void concurrentPaymentRecordsOneTransactionAndRejectsChangedVoucher()throws Exception {
        long id=request("reception");ok("POST","/api/v1/finance/refunds/"+id+"/approve","manager",Map.of("reason","核对"));String reference=UUID.randomUUID().toString();var pay=Map.of("amount",10,"channel","CASH","externalReference",reference,"occurredAt",LocalDateTime.now().minusDays(40).toString(),"note","核实发生时间");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{var f1=pool.submit(()->ok("POST","/api/v1/finance/refunds/"+id+"/pay","manager",pay));var f2=pool.submit(()->ok("POST","/api/v1/finance/refunds/"+id+"/pay","manager",pay));assertEquals("PAID",f1.get().path("status").asText());assertEquals("PAID",f2.get().path("status").asText());}finally{pool.shutdownNow();}
        var tx=payments.findByOrderIdInOrderByOccurredAtDesc(List.of(orderId)).stream().filter(p->"REFUND".equals(p.getTransactionType())).toList();assertEquals(1,tx.size());assertEquals(LocalDate.now().minusDays(40),tx.get(0).getOccurredAt().toLocalDate());
        assertNotEquals(0,call("POST","/api/v1/finance/refunds/"+id+"/pay","manager",Map.of("amount",10,"channel","CASH","externalReference","changed","occurredAt",LocalDateTime.now().toString(),"note","不能改写")).path("code").asInt());
    }
    long schedule(String type,Long owner)throws Exception {
        var c=new com.gym.fitness.module.course.entity.CourseEntity();c.setName("退款服务校验");c.setType(type);c.setVenueId(venueId);c.setMaxCapacity(5);c.setFee(BigDecimal.ZERO);c.setStatus("ACTIVE");courses.save(c);
        var s=new com.gym.fitness.module.course.entity.CourseScheduleEntity();s.setCourseId(c.getId());s.setCoachId(users.findByUsername("coach").orElseThrow().getId());s.setCoachName("测试教练");s.setVenueId(venueId);s.setScheduledStartTime(LocalDateTime.now().plusDays(1));s.setScheduledEndTime(LocalDateTime.now().plusDays(1).plusHours(1));s.setMaxStudents(5);s.setEnrolledCount(0);s.setStatus("SCHEDULED");schedules.save(s);return s.getId();
    }
    @Test void groupBookingBlocksApplicationAndPendingRefundBlocksGroupBooking()throws Exception {
        long sid=schedule("GROUP",memberId);long booking=ok("POST","/api/v1/admin/bookings","admin",Map.of("memberId",memberId,"scheduleId",sid)).path("id").asLong();
        assertNotEquals(0,call("POST","/api/v1/finance/refunds","reception",Map.of("entitlementId",entitlementId,"version",quote().path("version").asLong(),"reason","有预约","idempotencyKey",UUID.randomUUID().toString())).path("code").asInt());
        var b=bookings.findById(booking).orElseThrow();b.setStatus("CANCELLED");bookings.save(b);request("reception");assertNotEquals(0,call("POST","/api/v1/admin/bookings","admin",Map.of("memberId",memberId,"scheduleId",sid)).path("code").asInt());
    }
    @Test void privateCorrectionRestoresOriginalAllocationAfterRenewalAndPaidCannotReopen()throws Exception {
        memberId=members.findByUserId(users.findByUsername("member").orElseThrow().getId()).orElseThrow().getId();
        var p=new MembershipProductEntity();p.setName("退款私教"+UUID.randomUUID());p.setCategory("PACKAGE");p.setProductType("PRIVATE_PACKAGE");p.setValidityDays(90);p.setTotalUses(3);p.setPrice(new BigDecimal("10.00"));p.setAccessAllowed(false);p.setStatus("ACTIVE");p.setVenueId(venueId);products.save(p);
        orderId=ok("POST","/api/v1/commercial/orders","admin",Map.of("memberId",memberId,"productId",p.getId(),"paidAmount",10,"paymentMethod", "CASH", "idempotencyKey", java.util.UUID.randomUUID().toString())).path("id").asLong();entitlementId=entitlements.findByMemberIdOrderByIdDesc(memberId).get(0).getId();long sid=schedule("PRIVATE",memberId);
        long booking=ok("POST","/api/v1/admin/bookings","admin",Map.of("memberId",memberId,"scheduleId",sid,"entitlementId",entitlementId)).path("id").asLong();var s=schedules.findById(sid).orElseThrow();s.setScheduledStartTime(LocalDateTime.now().minusHours(2));s.setScheduledEndTime(LocalDateTime.now().minusHours(1));schedules.save(s);
        ok("POST","/api/v1/admin/bookings/"+booking+"/check-in","coach",Map.of());ok("POST","/api/v1/training/lessons/"+booking+"/submit","coach",Map.of());assertEquals(new BigDecimal("3.33"),ok("POST","/api/v1/training/lessons/"+booking+"/confirm","member",Map.of()).path("unitPrice").decimalValue().setScale(2));
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/renew","admin",Map.of("additionalUses",2,"amount",20,"paymentMethod","CASH"));assertEquals(new BigDecimal("26.67"),quote().path("refundableAmount").decimalValue().setScale(2));
        ok("POST","/api/v1/training/lessons/"+booking+"/reverse","manager",Map.of("reason","原签课纠错"));assertEquals(new BigDecimal("30.00"),quote().path("refundableAmount").decimalValue().setScale(2));assertEquals(5,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
    }
    @Test void legacyTypeMustBeAttestedAndZeroServicesCannotHavePositiveUnusedValue()throws Exception {
        legacy();var q=quote();String path="/api/v1/finance/entitlements/"+entitlementId+"/reconcile";
        assertNotEquals(0,call("POST",path,"manager",Map.of("version",q.path("version").asLong(),"remainingUses",3,"reason","缺少历史商品种类依据","sources",List.of(Map.of("orderId",orderId,"unusedAmount",10)))).path("code").asInt());
        var e=entitlements.findById(entitlementId).orElseThrow();e.setRemainingUses(0);e.setStatus("USED_UP");entitlements.save(e);
        assertNotEquals(0,call("POST",path,"manager",Map.of("version",quote().path("version").asLong(),"remainingUses",0,"reason","无剩余服务不可填正值","sources",List.of(Map.of("orderId",orderId,"unusedAmount",10,"productType","VISIT_CARD")))).path("code").asInt());
    }
    @Test void expiredLegacyTermCannotInventFutureServiceDays()throws Exception {
        legacy();var e=entitlements.findById(entitlementId).orElseThrow();e.setRemainingUses(null);e.setTotalUses(null);e.setValidFrom(LocalDate.now().minusDays(30));e.setValidUntil(LocalDate.now().minusDays(1));e.setStatus("EXPIRED");entitlements.save(e);
        var body=new HashMap<String,Object>();body.put("version",quote().path("version").asLong());body.put("remainingUses",null);body.put("reason","过期不得凭空增加未来服务日");body.put("sources",List.of(Map.of("orderId",orderId,"unusedAmount",10,"productType","MONTH_CARD","totalUnits",3,"startDate",LocalDate.now().toString())));
        assertNotEquals(0,call("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",body).path("code").asInt());
        e=entitlements.findById(entitlementId).orElseThrow();e.setStatus("ACTIVE");e.setValidUntil(LocalDate.now().plusDays(2));entitlements.save(e);
        body.put("sources",List.of(Map.of("orderId",orderId,"unusedAmount",10,"productType","MONTH_CARD","totalUnits",4,"startDate",LocalDate.now().toString())));
        assertNotEquals(0,call("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",body).path("code").asInt());
    }
    @Test void headquartersCanVerifyTransferredValueWithoutRewritingMoney()throws Exception {
        Long target=members.findByUserId(users.findByUsername("member").orElseThrow().getId()).orElseThrow().getId();
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/transfer","manager",Map.of("targetMemberId",target));var q=quote();assertEquals("VERIFIED",q.path("verificationStatus").asText());assertTrue(q.path("blockedReasons").size()>0);
        String path="/api/v1/finance/entitlements/"+entitlementId+"/verify-transfer";var body=Map.of("version",q.path("version").asLong(),"reason","核验转让合法依据","transferEvidence","原付款凭证已核对，当前持有人与原付款人同意退款受领安排");
        assertTrue(com.gym.fitness.security.RolePermissionCatalog.permissionsFor("SUPER_ADMIN").contains("VALUE_TRANSFER_VERIFY"));assertFalse(com.gym.fitness.security.RolePermissionCatalog.permissionsFor("MANAGER").contains("VALUE_TRANSFER_VERIFY"));assertEquals(403,response("POST",path,"manager",body).statusCode());var result=ok("POST",path,"admin",body);assertEquals(new BigDecimal("10.00"),result.path("refundableAmount").decimalValue().setScale(2));assertEquals(3,result.path("remainingUses").asInt());assertEquals(1,funding.findByEntitlementIdOrderByOrderIdAsc(entitlementId).size());
        assertEquals(new BigDecimal("0.00"),orders.findById(orderId).orElseThrow().getRefundedAmount().setScale(2));
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/transfer","manager",Map.of("targetMemberId",memberId));
        assertNull(valueAccounts.findByEntitlementId(entitlementId).orElseThrow().getTransferEvidence());
        var evidenceEvent=valueEvents.findByEntitlementIdOrderByIdAsc(entitlementId).stream().filter(v->"VERIFY_TRANSFER".equals(v.getEventType())).findFirst().orElseThrow();
        assertTrue(json.valueToTree(evidenceEvent).path("detailsJson").asText().contains("原付款凭证已核对"),"Prior verified evidence must remain in immutable audit history after another transfer");
    }
    @Test void noOtherReviewerBlocksRequestAndMemberProgressIsRedacted()throws Exception {
        var changed=new ArrayList<com.gym.fitness.module.auth.entity.SysUserEntity>();
        try{for(var u:users.findAll())if(!"reception".equals(u.getUsername())&&Set.of("SUPER_ADMIN","MANAGER").contains(u.getRole().getCode())&&"ACTIVE".equals(u.getStatus())){u.setStatus("DISABLED");users.save(u);changed.add(u);}
            assertNotEquals(0,call("POST","/api/v1/finance/refunds","reception",Map.of("entitlementId",entitlementId,"version",valueAccounts.findByEntitlementId(entitlementId).orElseThrow().getVersion(),"reason","没有另一审核人","idempotencyKey",UUID.randomUUID().toString())).path("code").asInt());
        }finally{for(var u:changed){u.setStatus("ACTIVE");users.save(u);}}
        long foreign=request("reception");var own=ok("GET","/api/v1/finance/me/refunds","member",null);for(var r:own)assertNotEquals(foreign,r.path("id").asLong());
        Long originalMember=memberId;memberId=members.findByUserId(users.findByUsername("member").orElseThrow().getId()).orElseThrow().getId();termSale();long id=request("reception");
        var rows=ok("GET","/api/v1/finance/me/refunds?memberId="+originalMember,"member",null);JsonNode row=null;for(var r:rows)if(r.path("id").asLong()==id)row=r;assertNotNull(row);for(String key:List.of("requestedBy","approvedBy","externalReference","channel","audit","allocations","memberName","reason"))assertFalse(row.has(key),key);
        ok("POST","/api/v1/finance/refunds/"+id+"/cancel","reception",Map.of("reason","测试完成"));
    }
    @Test void invalidPaymentAndIdempotencyMismatchDoNotWriteMoney()throws Exception {
        long version=quote().path("version").asLong();String key=UUID.randomUUID().toString();long id=ok("POST","/api/v1/finance/refunds","reception",Map.of("entitlementId",entitlementId,"version",version,"reason","核验","idempotencyKey",key)).path("id").asLong();
        assertNotEquals(0,call("POST","/api/v1/finance/refunds","reception",Map.of("entitlementId",entitlementId,"version",version,"reason","不同内容","idempotencyKey",key)).path("code").asInt());ok("POST","/api/v1/finance/refunds/"+id+"/approve","manager",Map.of("reason","同意"));
        for(String amount:List.of("0","9.99","10.001"))assertNotEquals(0,call("POST","/api/v1/finance/refunds/"+id+"/pay","manager",Map.of("amount",new BigDecimal(amount),"channel","CASH","externalReference",UUID.randomUUID().toString(),"occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"note","校验金额")).path("code").asInt());
        assertNotEquals(0,call("POST","/api/v1/finance/refunds/"+id+"/pay","manager",Map.of("amount",10,"channel","CASH","externalReference",UUID.randomUUID().toString(),"occurredAt",LocalDateTime.now().plusDays(1).toString(),"note","不能未来")).path("code").asInt());
        assertNotEquals(0,call("POST","/api/v1/finance/refunds/"+id+"/pay","reception",Map.of("amount",10,"channel","CASH","externalReference",UUID.randomUUID().toString(),"occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"note","前台不得付款登记")).path("code").asInt());
        assertEquals(new BigDecimal("0.00"),orders.findById(orderId).orElseThrow().getRefundedAmount().setScale(2));assertEquals("ACTIVE",entitlements.findById(entitlementId).orElseThrow().getStatus());
    }
    @Test void pendingRefundBlocksMutationsAndOtherVenueCannotReadOrAct()throws Exception {
        long id=request("reception");for(String action:List.of("freeze","unfreeze"))assertNotEquals(0,call("POST","/api/v1/commercial/entitlements/"+entitlementId+"/"+action,"manager",Map.of("note","办理中禁止")).path("code").asInt());
        assertNotEquals(0,call("POST","/api/v1/commercial/access/scan","admin",Map.of("token",freshToken())).path("code").asInt());
        var manager=users.findByUsername("manager").orElseThrow();long ownVenue=manager.getVenueId();Long other=venues.findAll().stream().map(v->v.getId()).filter(v->!v.equals(ownVenue)).findFirst().orElseThrow();
        try{manager.setVenueId(other);users.save(manager);assertNotEquals(0,call("GET","/api/v1/finance/entitlements/"+entitlementId+"/quote","manager",null).path("code").asInt());assertNotEquals(0,call("POST","/api/v1/finance/refunds/"+id+"/approve","manager",Map.of("reason","跨店拒绝")).path("code").asInt());for(var r:ok("GET","/api/v1/finance/refunds","manager",null))assertNotEquals(id,r.path("id").asLong());}finally{manager.setVenueId(ownVenue);users.save(manager);}
    }
    @Test void refundedWaitlistCandidateCannotRollBackAnotherMembersCancellation()throws Exception {
        long id=request("reception");long sid=schedule("GROUP",memberId);Long other=members.findByUserId(users.findByUsername("member").orElseThrow().getId()).orElseThrow().getId();
        long booking=ok("POST","/api/v1/admin/bookings","admin",Map.of("memberId",other,"scheduleId",sid)).path("id").asLong();
        // Reproduce a stale imported candidate. Eligibility failure must not poison the cancellation transaction.
        var candidate=new com.gym.fitness.module.booking.entity.BookingEntity();candidate.setMemberId(memberId);candidate.setScheduleId(sid);candidate.setStatus("WAITLISTED");candidate.setWaitlistPosition(1);candidate.setBookedAt(LocalDateTime.now());bookings.save(candidate);
        ok("PUT","/api/v1/admin/bookings/"+booking+"/cancel","admin",null);assertEquals("CANCELLED",bookings.findById(booking).orElseThrow().getStatus());assertEquals("CANCELLED",bookings.findById(candidate.getId()).orElseThrow().getStatus());
    }
    @Test void productEditCannotReclassifySoldFundsOrEntitlement()throws Exception {
        var e=entitlements.findById(entitlementId).orElseThrow();var p=products.findById(e.getProductId()).orElseThrow();p.setProductType("PRIVATE_PACKAGE");products.save(p);
        assertEquals("TIMES_CARD",quote().path("productType").asText());var rows=ok("GET","/api/v1/commercial/entitlements?memberId="+memberId,"admin",null);assertEquals("TIMES_CARD",rows.get(0).path("productType").asText());
        ok("POST","/api/v1/commercial/access/scan","admin",Map.of("token",freshToken()));assertEquals(new BigDecimal("6.67"),quote().path("refundableAmount").decimalValue().setScale(2));
    }
    @Test void frozenLegacyReconciliationDoesNotAddAlreadyPausedDaysTwice()throws Exception {
        legacy();var e=entitlements.findById(entitlementId).orElseThrow();e.setRemainingUses(null);e.setTotalUses(null);e.setValidFrom(LocalDate.now().minusDays(3));e.setValidUntil(LocalDate.now().minusDays(1));e.setStatus("FROZEN");e.setFrozenAt(LocalDate.now().minusDays(2).atStartOfDay());entitlements.save(e);
        var body=new HashMap<String,Object>();body.put("version",quote().path("version").asLong());body.put("remainingUses",null);body.put("reason","冻结前已消费一天，核对两天剩余服务");body.put("sources",List.of(Map.of("orderId",orderId,"unusedAmount",new BigDecimal("6.67"),"productType","MONTH_CARD","totalUnits",2,"startDate",LocalDate.now().toString())));
        ok("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",body);ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/unfreeze","manager",Map.of("note","恢复"));
        assertEquals(LocalDate.now().plusDays(1),entitlements.findById(entitlementId).orElseThrow().getValidUntil());assertEquals(LocalDate.now(),periods.findByEntitlementIdOrderByIdAsc(entitlementId).get(0).getNextServiceDate());assertEquals(2,quote().path("remainingServiceDays").asInt());
    }
    long pendingPrivateLesson()throws Exception {
        memberId=members.findByUserId(users.findByUsername("member").orElseThrow().getId()).orElseThrow().getId();
        var p=new MembershipProductEntity();p.setName("待签课测试"+UUID.randomUUID());p.setCategory("PACKAGE");p.setProductType("PRIVATE_PACKAGE");p.setValidityDays(90);p.setTotalUses(3);p.setPrice(new BigDecimal("10.00"));p.setAccessAllowed(false);p.setStatus("ACTIVE");p.setVenueId(venueId);products.save(p);
        orderId=ok("POST","/api/v1/commercial/orders","admin",Map.of("memberId",memberId,"productId",p.getId(),"paidAmount",10,"paymentMethod", "CASH", "idempotencyKey", java.util.UUID.randomUUID().toString())).path("id").asLong();entitlementId=entitlements.findByMemberIdOrderByIdDesc(memberId).get(0).getId();long sid=schedule("PRIVATE",memberId);
        long booking=ok("POST","/api/v1/admin/bookings","admin",Map.of("memberId",memberId,"scheduleId",sid,"entitlementId",entitlementId)).path("id").asLong();var s=schedules.findById(sid).orElseThrow();s.setScheduledStartTime(LocalDateTime.now().minusHours(2));s.setScheduledEndTime(LocalDateTime.now().minusHours(1));schedules.save(s);
        ok("POST","/api/v1/admin/bookings/"+booking+"/check-in","coach",Map.of());ok("POST","/api/v1/training/lessons/"+booking+"/submit","coach",Map.of());return booking;
    }
    @Test void pendingLessonOnAnotherPackageBlocksTimeCardRefund()throws Exception {
        long booking=pendingPrivateLesson();termSale();
        try{assertNotEquals(0,call("POST","/api/v1/finance/refunds","reception",Map.of("entitlementId",entitlementId,"version",quote().path("version").asLong(),"reason","本人还有待签课","idempotencyKey",UUID.randomUUID().toString())).path("code").asInt());}
        finally{ok("POST","/api/v1/training/lessons/"+booking+"/confirm","member",Map.of());}
    }
    @Test void pendingAndPaidRefundCannotBeReopenedByPrivateCorrection()throws Exception {
        long booking=pendingPrivateLesson();ok("POST","/api/v1/training/lessons/"+booking+"/confirm","member",Map.of());long id=request("reception");
        assertNotEquals(0,call("POST","/api/v1/training/lessons/"+booking+"/reverse","manager",Map.of("reason","办理中禁止")).path("code").asInt());
        ok("POST","/api/v1/finance/refunds/"+id+"/approve","manager",Map.of("reason","已核对"));ok("POST","/api/v1/finance/refunds/"+id+"/pay","manager",Map.of("amount",new BigDecimal("6.67"),"channel","CASH","externalReference",UUID.randomUUID().toString(),"occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"note","线下凭证"));
        assertNotEquals(0,call("POST","/api/v1/training/lessons/"+booking+"/reverse","manager",Map.of("reason","已退款禁止")).path("code").asInt());assertEquals(0,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());assertEquals(new BigDecimal("0.00"),quote().path("refundableAmount").decimalValue().setScale(2));
    }
    @Test void paidTimeCardHasNoRemainingServiceWhileKeepingPeriodHistory()throws Exception {
        termSale();long id=request("reception");ok("POST","/api/v1/finance/refunds/"+id+"/approve","manager",Map.of("reason","终止全部服务"));ok("POST","/api/v1/finance/refunds/"+id+"/pay","manager",Map.of("amount",10,"channel","CASH","externalReference",UUID.randomUUID().toString(),"occurredAt",LocalDateTime.now().minusMinutes(1).toString(),"note","线下凭证"));
        assertEquals(0,quote().path("remainingServiceDays").asInt());assertEquals(3,periods.findByEntitlementIdOrderByIdAsc(entitlementId).get(0).getTotalDays());
    }
    @Test void timeConsumptionBelongsToEachActualServiceMonth()throws Exception {
        termSale();// 起始日取"上个月 1 号的前一天"：三天仍然跨越月份边界（用例本意），但**全部落在过去**。
        // 原来取的是"本月 1 号前一天"，第三天会落在下个月 1–2 号之后——每月 1–2 号跑必红。
        LocalDate start=LocalDate.now().withDayOfMonth(1).minusMonths(1).minusDays(1);var p=periods.findByEntitlementIdOrderByIdAsc(entitlementId).get(0);p.setStartDate(start);p.setNextServiceDate(start);periods.save(p);
        assertEquals(new BigDecimal("0.00"),quote().path("refundableAmount").decimalValue().setScale(2));var events=valueEvents.findByEntitlementIdOrderByIdAsc(entitlementId).stream().filter(v->"TIME_CONSUME".equals(v.getEventType())).toList();assertEquals(3,events.size());
        long[] expected={-333,-333,-334};for(int i=0;i<3;i++){assertEquals(expected[i],events.get(i).getDeltaCents());assertEquals(start.plusDays(i).atTime(23,59,59),events.get(i).getOccurredAt());assertEquals(start.plusDays(i).toString(),json.readTree(events.get(i).getDetailsJson()).path("serviceDate").asText());}
        assertEquals(0,funding.findByOrderId(orderId).orElseThrow().getRemainingCents());assertEquals(0,valueAccounts.findByEntitlementId(entitlementId).orElseThrow().getBalanceCents());assertEquals(0,events.get(2).getBalanceAfterCents());
        quote();assertEquals(3,valueEvents.findByEntitlementIdOrderByIdAsc(entitlementId).stream().filter(v->"TIME_CONSUME".equals(v.getEventType())).count());
    }
    @Test void cancelledHistoricalOrderCannotFundAReconciliation()throws Exception {
        legacy();var order=orders.findById(orderId).orElseThrow();order.setStatus("CANCELLED");orders.save(order);
        assertNotEquals(0,call("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",Map.of("version",quote().path("version").asLong(),"remainingUses",3,"reason","取消订单不能作实收依据","sources",List.of(Map.of("orderId",orderId,"unusedAmount",10,"productType","TIMES_CARD")))).path("code").asInt());
    }
    @Test void exhaustedLegacyTermSourceCanBeReconciledWithKnownUnconsumedRenewal()throws Exception {
        legacy();var e=entitlements.findById(entitlementId).orElseThrow();e.setRemainingUses(null);e.setTotalUses(null);e.setValidFrom(LocalDate.now().minusDays(30));e.setValidUntil(LocalDate.now().minusDays(1));e.setStatus("EXPIRED");entitlements.save(e);
        var product=products.findById(e.getProductId()).orElseThrow();product.setProductType("MONTH_CARD");products.save(product);
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/renew","manager",Map.of("additionalDays",3,"amount",20,"paymentMethod","CASH"));
        var renewal=funding.findByEntitlementIdOrderByOrderIdAsc(entitlementId).get(0);var originalPeriod=periods.findByEntitlementIdOrderByIdAsc(entitlementId).get(0);
        var body=new HashMap<String,Object>();body.put("version",quote().path("version").asLong());body.put("remainingUses",null);body.put("reason","原服务期已耗尽，续费三天尚未使用");
        body.put("sources",List.of(Map.of("orderId",orderId,"unusedAmount",0,"productType","MONTH_CARD","totalUnits",0),Map.of("orderId",renewal.getOrderId(),"unusedAmount",20,"totalUnits",3,"startDate",LocalDate.now().toString())));
        var result=ok("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",body);
        assertEquals("VERIFIED",result.path("verificationStatus").asText());assertEquals(new BigDecimal("20.00"),result.path("refundableAmount").decimalValue().setScale(2));assertEquals(3,result.path("remainingServiceDays").asInt());assertEquals(2,result.path("funding").size());
        assertEquals(0,funding.findByOrderId(orderId).orElseThrow().getRemainingCents());assertEquals(2000,funding.findByOrderId(renewal.getOrderId()).orElseThrow().getRemainingCents());assertEquals(2000,valueAccounts.findByEntitlementId(entitlementId).orElseThrow().getBalanceCents());
        var remainingPeriods=periods.findByEntitlementIdOrderByIdAsc(entitlementId);assertEquals(1,remainingPeriods.size());assertEquals(originalPeriod.getId(),remainingPeriods.get(0).getId());assertEquals(renewal.getId(),remainingPeriods.get(0).getFundingId());assertEquals(3,remainingPeriods.get(0).getTotalDays());
        assertEquals("PENDING",ok("POST","/api/v1/finance/refunds","reception",Map.of("entitlementId",entitlementId,"version",result.path("version").asLong(),"reason","仅退未使用续费","idempotencyKey",UUID.randomUUID().toString())).path("status").asText());
    }
    @Test void zeroPriceFutureTermStillHasItsDeclaredServiceDays()throws Exception {
        legacy();var e=entitlements.findById(entitlementId).orElseThrow();e.setRemainingUses(null);e.setTotalUses(null);e.setValidUntil(LocalDate.now().plusDays(2));entitlements.save(e);
        var zeroPriceOrder=orders.findById(orderId).orElseThrow();zeroPriceOrder.setPaidAmount(BigDecimal.ZERO);zeroPriceOrder.setOriginalAmount(BigDecimal.ZERO);orders.save(zeroPriceOrder);
        var body=new HashMap<String,Object>();body.put("version",quote().path("version").asLong());body.put("remainingUses",null);body.put("reason","已核实三天零价服务仍未使用");body.put("sources",List.of(Map.of("orderId",orderId,"unusedAmount",0,"productType","MONTH_CARD","totalUnits",3,"startDate",LocalDate.now().toString())));
        var result=ok("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",body);assertEquals(new BigDecimal("0.00"),result.path("refundableAmount").decimalValue().setScale(2));assertEquals(3,result.path("remainingServiceDays").asInt());var remainingPeriods=periods.findByEntitlementIdOrderByIdAsc(entitlementId);assertEquals(1,remainingPeriods.size());assertEquals(3,remainingPeriods.get(0).getTotalDays());assertEquals(0,remainingPeriods.get(0).getTotalCents());
    }
    @Test void exhaustedTermRequiresExplicitZeroDaysAndZeroValue()throws Exception {
        legacy();var e=entitlements.findById(entitlementId).orElseThrow();e.setRemainingUses(null);e.setTotalUses(null);e.setValidUntil(LocalDate.now().minusDays(1));e.setStatus("EXPIRED");entitlements.save(e);
        var body=new HashMap<String,Object>();body.put("version",quote().path("version").asLong());body.put("remainingUses",null);body.put("reason","不得将缺失或负天数当作已耗尽");
        for(var source:List.of(Map.of("orderId",orderId,"unusedAmount",1,"productType","MONTH_CARD","totalUnits",0),Map.of("orderId",orderId,"unusedAmount",0,"productType","MONTH_CARD"),Map.of("orderId",orderId,"unusedAmount",0,"productType","MONTH_CARD","totalUnits",-1))){body.put("sources",List.of(source));assertNotEquals(0,call("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",body).path("code").asInt());}
        assertEquals("UNVERIFIED",quote().path("verificationStatus").asText());assertTrue(funding.findByEntitlementIdOrderByOrderIdAsc(entitlementId).isEmpty());assertTrue(periods.findByEntitlementIdOrderByIdAsc(entitlementId).isEmpty());
    }
    @Test void explicitlyExhaustedKnownPeriodDoesNotRetainRefundableServiceDays()throws Exception {
        legacy();var e=entitlements.findById(entitlementId).orElseThrow();e.setRemainingUses(null);e.setTotalUses(null);e.setValidFrom(LocalDate.now().minusDays(30));e.setValidUntil(LocalDate.now().minusDays(4));e.setStatus("EXPIRED");entitlements.save(e);
        var product=products.findById(e.getProductId()).orElseThrow();product.setProductType("MONTH_CARD");products.save(product);
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/renew","manager",Map.of("additionalDays",3,"amount",20,"paymentMethod","CASH"));
        var exhausted=funding.findByEntitlementIdOrderByOrderIdAsc(entitlementId).get(0);var exhaustedPeriod=periods.findByEntitlementIdOrderByIdAsc(entitlementId).get(0);exhaustedPeriod.setStartDate(LocalDate.now().minusDays(3));exhaustedPeriod.setNextServiceDate(LocalDate.now().minusDays(3));periods.save(exhaustedPeriod);
        e=entitlements.findById(entitlementId).orElseThrow();e.setValidUntil(LocalDate.now().minusDays(1));entitlements.save(e);
        ok("POST","/api/v1/commercial/entitlements/"+entitlementId+"/renew","manager",Map.of("additionalDays",3,"amount",30,"paymentMethod","CASH"));var future=funding.findByEntitlementIdOrderByOrderIdAsc(entitlementId).get(1);
        var body=new HashMap<String,Object>();body.put("version",quote().path("version").asLong());body.put("remainingUses",null);body.put("reason","原期及上一续费均已耗尽，保留下期三天");body.put("sources",List.of(Map.of("orderId",orderId,"unusedAmount",0,"productType","MONTH_CARD","totalUnits",0),Map.of("orderId",exhausted.getOrderId(),"unusedAmount",0,"totalUnits",0),Map.of("orderId",future.getOrderId(),"unusedAmount",30,"totalUnits",3,"startDate",LocalDate.now().toString())));
        var result=ok("POST","/api/v1/finance/entitlements/"+entitlementId+"/reconcile","manager",body);assertEquals(3,result.path("remainingServiceDays").asInt());assertEquals(new BigDecimal("30.00"),result.path("refundableAmount").decimalValue().setScale(2));
        var saved=periods.findById(exhaustedPeriod.getId()).orElseThrow();assertEquals(3,saved.getTotalDays());assertEquals(3,saved.getConsumedDays());assertEquals(2000,saved.getTotalCents());assertEquals(2,periods.findByEntitlementIdOrderByIdAsc(entitlementId).size());assertEquals(0,funding.findByOrderId(exhausted.getOrderId()).orElseThrow().getRemainingCents());
    }
}
