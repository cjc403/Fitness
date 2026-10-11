package com.gym.fitness;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.organization.repository.*;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cashshift;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE", "spring.jpa.hibernate.ddl-auto=create-drop","app.ai.enabled=false","fitness.operations.enabled=false"})
@AutoConfigureMockMvc @Transactional
class CashShiftIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void postgres(org.springframework.test.context.DynamicPropertyRegistry r) {
        String url=System.getProperty("cash.test.pgurl");
        if(url!=null&&!url.isBlank()) {
            if(!url.equals("jdbc:postgresql://127.0.0.1:55449/cash_junit"))throw new IllegalArgumentException("Only isolated cash_junit allowed");
            r.add("spring.datasource.url",()->url);r.add("spring.datasource.username",()->"fitness_user");r.add("spring.datasource.password",()->"isolated-test");
            r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");r.add("spring.jpa.hibernate.ddl-auto",()->"validate");r.add("spring.flyway.enabled",()->"true");
        }
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired SysUserRepository users;
    @Autowired RoleRepository roles; @Autowired VenueRepository venues; @Autowired JwtTokenProvider jwt;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void receptionCanOpenOneVenueShiftAndReadIt() throws Exception {
        var u=account("RECEPTION",0);var d=write(u,"",Map.of("idempotencyKey",key()),0);
        assertThat(d.path("shift").path("status").asText()).isEqualTo("OPEN");
        assertThat(d.path("channels").size()).isEqualTo(4);
        assertThat(read(u,"/"+d.path("shift").path("id").asLong()).path("shift").path("venueId").asLong()).isEqualTo(u.getVenueId());
    }
    @Test void firstShiftRecordsExistingVenueLedgerBaseline() throws Exception {
        var u=account("MANAGER",0);var d=write(u,"",Map.of("idempotencyKey",key()),0);
        long expected=jdbc.queryForObject("select coalesce(max(p.id),0) from payment_transaction p join sales_order o on o.id=p.order_id where o.venue_id=?",Long.class,u.getVenueId());
        assertThat(d.path("shift").path("baselinePaymentId").asLong()).isEqualTo(expected);
        assertThat(d.path("preview").path("paymentCount").asLong()).isZero();
    }
    @Test void disallowedRoleAndForeignVenueCannotReadOrOpen() throws Exception {
        var u=account("RECEPTION",0);var foreign=account("MANAGER",1);var d=write(u,"",Map.of("idempotencyKey",key()),0);
        readCode(foreign,"/"+d.path("shift").path("id").asLong(),1003);
        write(u,"",Map.of("venueId",foreign.getVenueId(),"idempotencyKey",key()),1003);
        readCode(account("SALES",0),"/workbench",1003);
    }

    @Autowired com.gym.fitness.module.member.repository.MemberRepository members;
    @Autowired com.gym.fitness.module.commercial.repository.MembershipProductRepository products;
    @Autowired com.gym.fitness.module.commercial.repository.SalesOrderRepository orders;
    @Autowired com.gym.fitness.module.commercial.repository.PaymentTransactionRepository transactions;
    @Autowired jakarta.persistence.EntityManager em;
    @Test void separateReceiptAndRefundDifferencesRequireReasonEvenWhenNetCancels() throws Exception {
        var u=account("RECEPTION",0);long id=open(u);var o=order(u);
        payment(o,"PAYMENT","CASH","100");payment(o,"REFUND","CASH","130");payment(o,"PAYMENT","WECHAT","200");payment(o,"REFUND","WECHAT","10");
        var d=read(u,"/"+id);assertThat(d.path("preview").path("systemNet").decimalValue()).isEqualByComparingTo("160");
        var request=submit(d,"105","135","195","10",null);write(u,"/"+id+"/submit",request,1001);
        request.put("differenceReason","收退款各差5元，微信少5元");d=write(u,"/"+id+"/submit",request,0);
        assertThat(d.path("shift").path("systemReceipts").decimalValue()).isEqualByComparingTo("300");
        assertThat(d.path("shift").path("systemRefunds").decimalValue()).isEqualByComparingTo("140");
        assertThat(d.path("shift").path("actualNet").decimalValue()).isEqualByComparingTo("155");
        assertThat(d.path("shift").path("netDifference").decimalValue()).isEqualByComparingTo("-5");
        var c=d.path("channels").get(0);assertThat(c.path("systemNet").decimalValue()).isEqualByComparingTo("-30");assertThat(c.path("actualNet").decimalValue()).isEqualByComparingTo("-30");assertThat(c.path("receiptsDifference").decimalValue()).isEqualByComparingTo("5");assertThat(c.path("refundsDifference").decimalValue()).isEqualByComparingTo("5");
        assertThat(d.path("payments").size()).isEqualTo(4);
    }
    @Test void flowChangedAfterPreviewRejectsOldFingerprintAndKeepsShiftOpen() throws Exception {
        var u=account("RECEPTION",0);long id=open(u);var d=read(u,"/"+id);var o=order(u);var p=payment(o,"REFUND"," NEW_CHANNEL ","12");
        jdbc.update("update payment_transaction set occurred_at=? where id=?",java.time.LocalDateTime.of(2020,1,1,0,0),p.getId());em.clear();
        write(u,"/"+id+"/submit",submit(d,"0","0","0","0",null),2600);d=read(u,"/"+id);
        assertThat(d.path("shift").path("status").asText()).isEqualTo("OPEN");assertThat(d.path("channels").size()).isEqualTo(5);
        assertThat(d.path("preview").path("systemNet").decimalValue()).isEqualByComparingTo("-12");
        var request=zeroSubmit(d);((List<Map<String,Object>>)request.get("channels")).add(actual("NEW_CHANNEL","0","12"));write(u,"/"+id+"/submit",request,0);
    }
    @Test void channelRowsMustBeExactExplicitAndValidMoney() throws Exception {
        var u=account("RECEPTION",0);long id=open(u);var d=read(u,"/"+id);
        for(String value:List.of("-1","0.001","10000000000")){var req=submit(d,value,"0","0","0","差异说明");write(u,"/"+id+"/submit",req,1001);}
        var req=zeroSubmit(d);((List<?>)req.get("channels")).remove(0);write(u,"/"+id+"/submit",req,1001);
        req=zeroSubmit(d);((List<Map<String,Object>>)req.get("channels")).add(actual("CASH","0","0"));write(u,"/"+id+"/submit",req,1001);
        req=zeroSubmit(d);((List<Map<String,Object>>)req.get("channels")).add(actual("EXTRA","0","0"));write(u,"/"+id+"/submit",req,1001);
        req=zeroSubmit(d);((List<Map<String,Object>>)req.get("channels")).get(0).put("actualReceipts",null);mvc.perform(post("/api/v1/cash-shifts/"+id+"/submit").header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(req))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        assertThat(read(u,"/"+id).path("shift").path("version").asLong()).isZero();
    }
    @Test void versionsOwnershipAndIndependentReviewAreEnforced() throws Exception {
        var u=account("RECEPTION",0);var other=account("RECEPTION",0);var manager=account("MANAGER",0);long id=open(u);var d=read(u,"/"+id);
        write(other,"/"+id+"/submit",zeroSubmit(d),1003);var req=zeroSubmit(d);req.put("expectedVersion",10);write(u,"/"+id+"/submit",req,2600);
        d=write(manager,"/"+id+"/submit",zeroSubmit(d),0);assertThat(d.path("shift").path("submittedBy").asLong()).isEqualTo(manager.getId());
        write(manager,"/"+id+"/review",review(1,"APPROVE",null),1003);write(u,"/"+id+"/review",review(1,"RETURN","说明"),1003);
        var reviewer=account("MANAGER",0);write(reviewer,"/"+id+"/review",review(1,"RETURN","  "),1001);write(reviewer,"/"+id+"/review",review(0,"APPROVE",null),2600);
        d=write(reviewer,"/"+id+"/review",review(1,"APPROVE",null),0);assertThat(d.path("shift").path("status").asText()).isEqualTo("APPROVED");
    }
    @Test void returnedResubmissionUsesFixedSnapshotAndAppendsBeforeAfterAudit() throws Exception {
        var u=account("RECEPTION",0);var manager=account("MANAGER",0);long id=open(u);var o=order(u);payment(o,"PAYMENT","CASH","10");
        var d=read(u,"/"+id);d=write(u,"/"+id+"/submit",submit(d,"10","0","0","0",null),0);var fingerprint=d.path("preview").path("ledgerFingerprint");var closed=d.path("shift").path("closedAt");
        d=write(manager,"/"+id+"/review",review(1,"RETURN","再核对"),0);long next=open(u);payment(o,"PAYMENT","CASH","9");
        d=write(u,"/"+id+"/submit",submit(d,"11","0","0","0","补正1元"),0);
        assertThat(d.path("preview").path("ledgerFingerprint")).isEqualTo(fingerprint);assertThat(d.path("shift").path("closedAt")).isEqualTo(closed);assertThat(d.path("paymentTotal").asInt()).isEqualTo(1);
        assertThat(d.path("audit").size()).isEqualTo(4);assertThat(d.path("audit").get(3).path("action").asText()).isEqualTo("RESUBMIT");
        var changes=json.readTree(d.path("audit").get(3).path("changesJson").asText());assertThat(changes.path("before").get(0).path("actualReceipts").decimalValue()).isEqualByComparingTo("10");assertThat(changes.path("after").get(0).path("actualReceipts").decimalValue()).isEqualByComparingTo("11");
        assertThat(read(u,"/"+next).path("preview").path("systemReceipts").decimalValue()).isEqualByComparingTo("9");
    }
    @Test void laterShiftCarriesBetweenShiftRegistrationsAndCountsEachPaymentOnlyOnce() throws Exception {
        var u=account("RECEPTION",0);var o=order(u);var old=payment(o,"PAYMENT","CASH","50");long first=open(u);
        assertThat(read(u,"/"+first).path("preview").path("historicalExcludedCount").asLong()).isGreaterThanOrEqualTo(1);
        var a=payment(o,"PAYMENT","CASH","10");var d=write(u,"/"+first+"/submit",submit(read(u,"/"+first),"10","0","0","0",null),0);
        var b=payment(o,"REFUND","CASH","2");jdbc.update("update payment_transaction set occurred_at=? where id=?",java.time.LocalDateTime.of(2019,1,1,0,0),b.getId());em.clear();long second=open(u);
        d=read(u,"/"+second);assertThat(d.path("preview").path("carriedOverCount").asInt()).isEqualTo(1);assertThat(d.path("preview").path("systemNet").decimalValue()).isEqualByComparingTo("-2");
        write(u,"/"+second+"/submit",submit(d,"0","2","0","0",null),0);
        assertThat(jdbc.queryForObject("select count(*) from cash_shift_payment where payment_transaction_id in (?,?)",Long.class,a.getId(),b.getId())).isEqualTo(2);
        assertThat(transactions.findById(old.getId()).orElseThrow().getAmount()).isEqualByComparingTo("50");assertThat(orders.findById(o.getId()).orElseThrow().getPaidAmount()).isEqualByComparingTo("1000");
    }
    @Test void requestReplaysBeforeVersionAndStateButRejectsChangedPayloadAndRoleDowngrade() throws Exception {
        var u=account("MANAGER",0);var request=new LinkedHashMap<String,Object>(Map.of("idempotencyKey",key()));var d=write(u,"",request,0);long id=d.path("shift").path("id").asLong();assertThat(write(u,"",request,0).path("shift").path("id").asLong()).isEqualTo(id);
        request.put("note","changed");write(u,"",request,1001);var submit=zeroSubmit(d);d=write(u,"/"+id+"/submit",submit,0);assertThat(write(u,"/"+id+"/submit",submit,0).path("audit").size()).isEqualTo(2);
        submit.put("handoverNote","changed");write(u,"/"+id+"/submit",submit,1001);
        var sales=roles.findByCode("SALES").orElseThrow();u.setRoleId(sales.getId());u.setRole(sales);users.saveAndFlush(u);write(u,"",Map.of("idempotencyKey",request.get("idempotencyKey")),1003);
    }
    @Test void workbenchSummaryIgnoresStatusPaginationAndHeadquartersRetainsVenueChoices() throws Exception {
        var u=account("RECEPTION",0);long id=open(u);write(u,"/"+id+"/submit",zeroSubmit(read(u,"/"+id)),0);long next=open(u);
        var admin=account("SUPER_ADMIN",0);var d=read(admin,"/workbench?venueId="+u.getVenueId()+"&status=OPEN&page=1&size=1");assertThat(d.path("summary").path("shiftCount").asInt()).isEqualTo(2);assertThat(d.path("summary").path("pendingReviewCount").asInt()).isEqualTo(1);assertThat(d.path("summary").path("openCount").asInt()).isEqualTo(1);assertThat(d.path("shifts").path("total").asInt()).isEqualTo(1);assertThat(d.path("venues").size()).isGreaterThanOrEqualTo(2);assertThat(d.path("currentShift").path("id").asLong()).isEqualTo(next);
        d=read(u,"/workbench?businessDate=2000-01-01");assertThat(d.path("currentShift").path("id").asLong()).isEqualTo(next);assertThat(d.path("summary").path("shiftCount").asInt()).isZero();
        assertThat(read(admin,"/workbench").path("currentShift").isNull()).isTrue();write(admin,"",Map.of("idempotencyKey",key()),1001);
    }
    @Test void unknownBlankAndSelfServiceChannelsAreRegisteredCashAndPaymentPagesAreBounded() throws Exception {
        var u=account("RECEPTION",0);long id=open(u);var o=order(u);payment(o,"PAYMENT"," ","2");payment(o,"PAYMENT","SELF_SERVICE","3");
        for(int i=0;i<101;i++)payment(o,"PAYMENT","ALIPAY","1");var d=read(u,"/"+id);assertThat(d.path("payments").size()).isEqualTo(100);assertThat(d.path("paymentTotal").asInt()).isEqualTo(103);
        var req=zeroSubmit(d);var cs=(List<Map<String,Object>>)req.get("channels");cs.get(2).put("actualReceipts",new java.math.BigDecimal("101"));cs.add(actual("UNKNOWN","2","0"));cs.add(actual("SELF_SERVICE","3","0"));write(u,"/"+id+"/submit",req,0);
        var page=read(u,"/"+id+"/payments?page=2&size=100");assertThat(page.path("records").size()).isEqualTo(3);assertThat(page.path("total").asInt()).isEqualTo(103);
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired javax.sql.DataSource datasource;
    @Autowired com.gym.fitness.module.cashshift.service.CashShiftService cashService;
    @Test void headquartersSwitchedTokenCanWriteWithAccountMutex() throws Exception {
        var admin=account("SUPER_ADMIN",0);long target=venues.findAll().get(1).getId();String switched="Bearer "+jwt.generateToken(admin.getId(),admin.getUsername(),"SUPER_ADMIN",target,0);
        mvc.perform(post("/api/v1/cash-shifts").header("Authorization",switched).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("venueId",target,"idempotencyKey",key())))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }
    @Test void allowedRolesStillRequireAuthorityAndExtraAuthorityCannotGrantOtherRoles() throws Exception {
        for(String role:List.of("SALES","COACH","MEMBER","RECEPTION")){var u=account(role,0);var principal=new com.gym.fitness.security.UserDetailsImpl();principal.setId(u.getId());principal.setRole(role);principal.setOrganizationId(u.getVenueId());principal.setPermissions("RECEPTION".equals(role)?List.of():List.of("CASH_SHIFT_READ","CASH_SHIFT_WRITE","CASH_SHIFT_REVIEW"));
            mvc.perform(get("/api/v1/cash-shifts/workbench").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1003));
        }
    }
    @Test void historicalExcludedCountStaysAtFirstBaselineAndCurrentShiftShowsLiveAmounts() throws Exception {
        var v=dedicatedVenue();dedicatedProduct(v.getId());var u=accountAt("RECEPTION",v);var o=order(u);payment(o,"PAYMENT","CASH","50");long first=open(u);payment(o,"PAYMENT","CASH","10");
        var d=read(u,"/workbench");assertThat(d.path("currentShift").path("systemReceipts").decimalValue()).isEqualByComparingTo("10");
        write(u,"/"+first+"/submit",submit(read(u,"/"+first),"10","0","0","0",null),0);long second=open(u);assertThat(read(u,"/"+second).path("preview").path("historicalExcludedCount").asInt()).isEqualTo(1);
    }
    @Test void persistedBusinessTimesAreShanghaiMicrosecondsAndClosedReadsLoadOnlyFirstPage() throws Exception {
        var clock=org.springframework.test.util.ReflectionTestUtils.getField(cashService,"businessClock");org.springframework.test.util.ReflectionTestUtils.setField(cashService,"businessClock",java.time.Clock.fixed(java.time.Instant.parse("2026-10-09T16:00:00.123456900Z"),java.time.ZoneId.of("Asia/Shanghai")));
        try {var u=account("RECEPTION",0);long id=open(u);var d=read(u,"/"+id);assertThat(d.path("shift").path("businessDate").asText()).isEqualTo("2026-10-10");assertThat(java.time.LocalDateTime.parse(d.path("shift").path("openedAt").asText())).isEqualTo(java.time.LocalDateTime.of(2026,10,10,0,0,0,123456000));var o=order(u);for(int i=0;i<103;i++)payment(o,"PAYMENT","CASH","1");write(u,"/"+id+"/submit",submit(read(u,"/"+id),"103","0","0","0",null),0);
            em.clear();var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);stats.clear();try{d=read(u,"/"+id);assertThat(d.path("payments").size()).isEqualTo(100);assertThat(d.path("paymentTotal").asInt()).isEqualTo(103);assertThat(stats.getEntityStatistics("com.gym.fitness.module.cashshift.entity.CashShiftPaymentEntity").getLoadCount()).isEqualTo(100);}finally{stats.setStatisticsEnabled(enabled);}
        }finally{org.springframework.test.util.ReflectionTestUtils.setField(cashService,"businessClock",clock);}
    }

    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void postgresDifferentActorsWaitForVenueThenOnlyOneOpenWins() throws Exception {
        pgOnly();var v=tx(()->dedicatedVenue());var a=tx(()->accountAt("RECEPTION",v));var b=tx(()->accountAt("MANAGER",v));var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(var gate=venueGate(v.getId())){var fa=pool.submit(()->raw(a,"/api/v1/cash-shifts",Map.of("idempotencyKey",key())));var fb=pool.submit(()->raw(b,"/api/v1/cash-shifts",Map.of("idempotencyKey",key())));awaitWaiters("venue",2);gate.commit();assertThat(List.of(fa.get(15,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt(),fb.get(15,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt())).containsExactlyInAnyOrder(0,2600);assertThat(jdbc.queryForObject("select count(*) from cash_shift where venue_id=? and status='OPEN'",Long.class,v.getId())).isEqualTo(1);}finally{stop(pool);}
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void postgresSameActorSubmitRetriesSerializeWithoutForeignKeyDeadlock() throws Exception {
        pgOnly();var v=tx(()->dedicatedVenue());var u=tx(()->accountAt("RECEPTION",v));long id=open(u);var req=zeroSubmit(read(u,"/"+id));var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(var gate=venueGate(v.getId())){var a=pool.submit(()->raw(u,"/api/v1/cash-shifts/"+id+"/submit",req));awaitWaiters("venue",1);var b=pool.submit(()->raw(u,"/api/v1/cash-shifts/"+id+"/submit",req));awaitWaiters("sys_user",1);gate.commit();assertThat(a.get(15,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()).isZero();assertThat(b.get(15,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()).isZero();assertThat(read(u,"/"+id).path("audit").size()).isEqualTo(2);}finally{stop(pool);}
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void postgresSameActorKeysCannotLeakAcrossVenues() throws Exception {
        pgOnly();var v1=tx(()->dedicatedVenue());var v2=tx(()->dedicatedVenue());var u=tx(()->accountAt("SUPER_ADMIN",v1));String key=key();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(var gate=venueGate(v1.getId())){var a=pool.submit(()->raw(u,"/api/v1/cash-shifts",Map.of("venueId",v1.getId(),"idempotencyKey",key)));awaitWaiters("venue",1);var b=pool.submit(()->raw(u,"/api/v1/cash-shifts",Map.of("venueId",v2.getId(),"idempotencyKey",key)));awaitWaiters("sys_user",1);gate.commit();assertThat(a.get(15,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()).isZero();assertThat(b.get(15,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()).isEqualTo(1001);assertThat(jdbc.queryForObject("select count(*) from cash_shift where venue_id=?",Long.class,v2.getId())).isZero();}finally{stop(pool);}
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void postgresRealSaleBeforeWaitingSubmitInvalidatesPreview() throws Exception {realSaleBoundary(false);}
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void postgresRealSaleAfterWaitingSubmitIsCarriedToNextShift() throws Exception {realSaleBoundary(true);}
    private void realSaleBoundary(boolean shiftFirst)throws Exception {
        pgOnly();var v=tx(()->dedicatedVenue());var u=tx(()->accountAt("RECEPTION",v));var seller=tx(()->accountAt("RECEPTION",v));var product=tx(()->dedicatedProduct(v.getId()));var member=tx(()->dedicatedMember(v.getId()));long id=open(u);var req=zeroSubmit(read(u,"/"+id));var sale=Map.of("memberId",member.getId(),"productId",product.getId(),"paymentMethod","CASH","paidAmount",20,"idempotencyKey",key());var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(var gate=venueGate(v.getId())){java.util.concurrent.Future<JsonNode> fsale,fshift;if(shiftFirst){fshift=pool.submit(()->raw(u,"/api/v1/cash-shifts/"+id+"/submit",req));awaitWaiters("venue",1);fsale=pool.submit(()->raw(seller,"/api/v1/commercial/orders",sale));}else{fsale=pool.submit(()->raw(seller,"/api/v1/commercial/orders",sale));awaitWaiters("venue",1);fshift=pool.submit(()->raw(u,"/api/v1/cash-shifts/"+id+"/submit",req));}awaitWaiters("venue",2);gate.commit();var sold=fsale.get(20,java.util.concurrent.TimeUnit.SECONDS);assertThat(sold.path("code").asInt()).isZero();assertThat(fshift.get(20,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()).isEqualTo(shiftFirst?0:2600);long oid=sold.path("data").path("id").asLong();var orderBefore=jdbc.queryForMap("select paid_amount,refunded_amount,status from sales_order where id=?",oid);long eid=jdbc.queryForObject("select id from member_entitlement where source_order_id=?",Long.class,oid);var entitlementBefore=jdbc.queryForMap("select remaining_uses,status from member_entitlement where id=?",eid);long next=shiftFirst?open(u):id;var d=read(u,"/"+next);assertThat(d.path("preview").path("systemReceipts").decimalValue()).isEqualByComparingTo("20");if(shiftFirst)assertThat(d.path("preview").path("carriedOverCount").asInt()).isEqualTo(1);write(u,"/"+next+"/submit",submit(d,"20","0","0","0",null),0);assertThat(jdbc.queryForMap("select paid_amount,refunded_amount,status from sales_order where id=?",oid)).isEqualTo(orderBefore);assertThat(jdbc.queryForMap("select remaining_uses,status from member_entitlement where id=?",eid)).isEqualTo(entitlementBefore);}finally{stop(pool);}
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void postgresRealBackdatedRefundBeforeWaitingSubmitInvalidatesPreview() throws Exception {
        pgOnly();var v=tx(()->dedicatedVenue());var u=tx(()->accountAt("RECEPTION",v));var manager=tx(()->accountAt("MANAGER",v));var p=tx(()->dedicatedProduct(v.getId()));var m=tx(()->dedicatedMember(v.getId()));
        var sale=raw(u,"/api/v1/commercial/orders",Map.of("memberId",m.getId(),"productId",p.getId(),"paymentMethod","CASH","paidAmount",20,"idempotencyKey",key()));assertThat(sale.path("code").asInt()).isZero();long oid=sale.path("data").path("id").asLong();long eid=jdbc.queryForObject("select id from member_entitlement where source_order_id=?",Long.class,oid);var quote=json.readTree(mvc.perform(get("/api/v1/finance/entitlements/"+eid+"/quote").header("Authorization",token(u))).andReturn().getResponse().getContentAsString()).path("data");var refund=raw(u,"/api/v1/finance/refunds",Map.of("entitlementId",eid,"version",quote.path("version").asLong(),"reason","真实退款测试","idempotencyKey",key()));assertThat(refund.path("code").asInt()).isZero();long rid=refund.path("data").path("id").asLong();assertThat(raw(manager,"/api/v1/finance/refunds/"+rid+"/approve",Map.of("reason","同意")).path("code").asInt()).isZero();long id=open(u);var req=zeroSubmit(read(u,"/"+id));var pay=Map.of("amount",20,"channel","CASH","externalReference",key(),"occurredAt","2020-01-01T12:00:00","note","补录过去发生退款");var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(var gate=venueGate(v.getId())){var fpay=pool.submit(()->raw(manager,"/api/v1/finance/refunds/"+rid+"/pay",pay));awaitWaiters("venue",1);var fshift=pool.submit(()->raw(u,"/api/v1/cash-shifts/"+id+"/submit",req));awaitWaiters("venue",2);gate.commit();assertThat(fpay.get(20,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()).isZero();assertThat(fshift.get(20,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()).isEqualTo(2600);var before=jdbc.queryForMap("select paid_amount,refunded_amount,status from sales_order where id=?",oid);var d=read(u,"/"+id);assertThat(d.path("preview").path("systemNet").decimalValue()).isEqualByComparingTo("-20");write(u,"/"+id+"/submit",submit(d,"0","20","0","0",null),0);assertThat(jdbc.queryForMap("select paid_amount,refunded_amount,status from sales_order where id=?",oid)).isEqualTo(before);}finally{stop(pool);}
    }
    <T> T tx(java.util.function.Supplier<T> action){return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(t->action.get());}
    void stop(java.util.concurrent.ExecutorService pool)throws Exception{pool.shutdownNow();pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);}
    void pgOnly(){org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("cash.test.pgurl")!=null,"Real PostgreSQL lock semantics");}
    java.sql.Connection venueGate(long id)throws Exception{var c=datasource.getConnection();c.setAutoCommit(false);try(var st=c.prepareStatement("select id from venue where id=? FOR NO KEY UPDATE")){st.setLong(1,id);st.executeQuery().close();}return c;}
    void awaitWaiters(String table,int count)throws Exception{long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<deadline){if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike ?",Long.class,"%"+table+"%")>=count)return;Thread.sleep(20);}throw new AssertionError("Expected real database lock waiters on "+table+": "+count);}
    JsonNode raw(SysUserEntity u,String path,Object body)throws Exception{return json.readTree(mvc.perform(post(path).header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andReturn().getResponse().getContentAsString());}
    com.gym.fitness.module.organization.entity.VenueEntity dedicatedVenue(){var v=new com.gym.fitness.module.organization.entity.VenueEntity();v.setCode("CS-"+key());v.setName("隔离交班测试店");v.setStatus("ACTIVE");return venues.saveAndFlush(v);}
    SysUserEntity accountAt(String role,com.gym.fitness.module.organization.entity.VenueEntity v){var u=new SysUserEntity();var r=roles.findByCode(role).orElseThrow();u.setUsername("cash-"+key());u.setRealName(u.getUsername());u.setRoleId(r.getId());u.setRole(r);u.setVenueId(v.getId());u.setVenue(v);u.setStatus("ACTIVE");u.setPasswordHash("unused");u.setMustChangePassword(false);return users.saveAndFlush(u);}
    com.gym.fitness.module.commercial.entity.MembershipProductEntity dedicatedProduct(Long venue){var p=new com.gym.fitness.module.commercial.entity.MembershipProductEntity();p.setVenueId(venue);p.setName("隔离次卡"+key());p.setCategory("CARD");p.setProductType("TIMES_CARD");p.setValidityDays(30);p.setTotalUses(10);p.setAccessAllowed(true);p.setPrice(new java.math.BigDecimal("20"));p.setStatus("ACTIVE");return products.saveAndFlush(p);}
    com.gym.fitness.module.member.entity.MemberEntity dedicatedMember(Long venue){var m=new com.gym.fitness.module.member.entity.MemberEntity();m.setVenueId(venue);m.setMemberNo("CSM-"+key());m.setRealName("隔离交班会员");m.setPhone("13800000000");m.setStatus("ACTIVE");m.setBlacklistFlag(false);return members.saveAndFlush(m);}
    long open(SysUserEntity u)throws Exception{return write(u,"",Map.of("idempotencyKey",key()),0).path("shift").path("id").asLong();}
    Map<String,Object> actual(String c,String r,String f){return new LinkedHashMap<>(Map.of("channel",c,"actualReceipts",new java.math.BigDecimal(r),"actualRefunds",new java.math.BigDecimal(f)));}
    Map<String,Object> submit(JsonNode d,String cr,String cf,String wr,String wf,String reason){var m=new LinkedHashMap<String,Object>();m.put("expectedVersion",d.path("shift").path("version").asLong());m.put("ledgerFingerprint",d.path("preview").path("ledgerFingerprint").asText());m.put("channels",new ArrayList<>(List.of(actual("CASH",cr,cf),actual("WECHAT",wr,wf),actual("ALIPAY","0","0"),actual("CARD","0","0"))));m.put("differenceReason",reason);m.put("idempotencyKey",key());return m;}
    Map<String,Object> zeroSubmit(JsonNode d){return submit(d,"0","0","0","0",null);}
    Map<String,Object> review(long v,String decision,String reason){var m=new LinkedHashMap<String,Object>();m.put("expectedVersion",v);m.put("decision",decision);m.put("reason",reason);m.put("idempotencyKey",key());return m;}
    com.gym.fitness.module.commercial.entity.SalesOrderEntity order(SysUserEntity u){var m=new com.gym.fitness.module.member.entity.MemberEntity();m.setMemberNo("CSM-"+key());m.setRealName("核对测试");m.setPhone("13800000000");m.setVenueId(u.getVenueId());m.setStatus("ACTIVE");m.setBlacklistFlag(false);members.saveAndFlush(m);var p=products.findAll().stream().filter(x->u.getVenueId().equals(x.getVenueId())).findFirst().orElseThrow();var o=new com.gym.fitness.module.commercial.entity.SalesOrderEntity();o.setOrderNo("CSO-"+key());o.setOrderType("NEW");o.setMemberId(m.getId());o.setProductId(p.getId());o.setVenueId(u.getVenueId());o.setOperatorId(u.getId());o.setPaidAmount(new java.math.BigDecimal("1000"));o.setOriginalAmount(new java.math.BigDecimal("1000"));o.setRefundedAmount(java.math.BigDecimal.ZERO);o.setStatus("PAID");return orders.saveAndFlush(o);}
    com.gym.fitness.module.commercial.entity.PaymentTransactionEntity payment(com.gym.fitness.module.commercial.entity.SalesOrderEntity o,String type,String channel,String amount){var p=new com.gym.fitness.module.commercial.entity.PaymentTransactionEntity();p.setTransactionNo("CSP-"+key());p.setOrderId(o.getId());p.setTransactionType(type);p.setPaymentMethod(channel);p.setAmount(new java.math.BigDecimal(amount));p.setStatus("SUCCESS");p.setOperatorId(o.getOperatorId());return transactions.saveAndFlush(p);}
    @Test void workbenchAggregatesWholeScopedDayWithoutHydratingOffPageShiftsOrChannels() throws Exception {
        var v=dedicatedVenue();dedicatedProduct(v.getId());var u=accountAt("RECEPTION",v);var manager=accountAt("MANAGER",v);var o=order(u);long first=0;
        for(int i=0;i<8;i++){long id=open(u);if(i==0)first=id;payment(o,"PAYMENT","CASH","10");write(u,"/"+id+"/submit",submit(read(u,"/"+id),"10","0","0","0",null),0);}
        var returned=write(manager,"/"+first+"/review",review(1,"RETURN","重新核对"),0);
        var before=read(u,"/workbench?status=RETURNED&size=1");assertThat(before.path("summary").path("shiftCount").asInt()).isEqualTo(8);assertThat(before.path("summary").path("returnedCount").asInt()).isEqualTo(1);assertThat(before.path("summary").path("systemReceipts").decimalValue()).isEqualByComparingTo("80");
        write(u,"/"+first+"/submit",submit(returned,"11","0","0","0","实核修正1元"),0);write(manager,"/"+first+"/review",review(3,"APPROVE",null),0);long current=open(u);payment(o,"PAYMENT","CASH","99");
        em.clear();var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);
        try{for(String query:List.of("/workbench?page=3&size=2","/workbench?status=APPROVED&page=1&size=1")){em.clear();stats.clear();var d=read(u,query);
            assertThat(d.path("summary").path("shiftCount").asInt()).isEqualTo(9);assertThat(d.path("summary").path("openCount").asInt()).isEqualTo(1);assertThat(d.path("summary").path("pendingReviewCount").asInt()).isEqualTo(7);assertThat(d.path("summary").path("approvedCount").asInt()).isEqualTo(1);assertThat(d.path("summary").path("returnedCount").asInt()).isZero();
            assertThat(d.path("summary").path("systemReceipts").decimalValue()).isEqualByComparingTo("80");assertThat(d.path("summary").path("actualReceipts").decimalValue()).isEqualByComparingTo("81");assertThat(d.path("summary").path("netDifference").decimalValue()).isEqualByComparingTo("1");assertThat(d.path("summary").path("approvedNet").decimalValue()).isEqualByComparingTo("11");assertThat(d.path("channels").get(0).path("systemReceipts").decimalValue()).isEqualByComparingTo("80");assertThat(d.path("channels").get(0).path("actualReceipts").decimalValue()).isEqualByComparingTo("81");assertThat(d.path("currentShift").path("id").asLong()).isEqualTo(current);assertThat(d.path("currentShift").path("systemReceipts").decimalValue()).isEqualByComparingTo("99");
            assertThat(stats.getEntityStatistics("com.gym.fitness.module.cashshift.entity.CashShiftEntity").getLoadCount()).as("Only page and independently selected current shift may hydrate").isLessThanOrEqualTo(3);
            assertThat(stats.getEntityStatistics("com.gym.fitness.module.cashshift.entity.CashShiftChannelEntity").getLoadCount()).as("Daily channel totals must be scalar grouped aggregates").isZero();
            assertThat(stats.getEntityStatistics(SysUserEntity.class.getName()).getLoadCount()).isLessThanOrEqualTo(1);
        }}finally{stats.setStatisticsEnabled(enabled);}
        var foreign=dedicatedVenue();var admin=accountAt("SUPER_ADMIN",foreign);var empty=read(admin,"/workbench?venueId="+foreign.getId());assertThat(empty.path("summary").path("shiftCount").asInt()).isZero();assertThat(empty.path("summary").path("actualReceipts").decimalValue()).isEqualByComparingTo("0");
        var scoped=read(admin,"/workbench?venueId="+v.getId()+"&status=OPEN&size=1");assertThat(scoped.path("summary").path("systemReceipts").decimalValue()).isEqualByComparingTo("80");assertThat(scoped.path("venues").size()).isGreaterThanOrEqualTo(2);
    }
    @Test void openPaymentPageHydratesOnlyRequestedRowsWhileDetailFingerprintCoversAllCandidates() throws Exception {
        var u=account("RECEPTION",0);long id=open(u);var o=order(u);var ids=new ArrayList<Long>();for(int i=0;i<151;i++)ids.add(payment(o,"PAYMENT","CASH","1").getId());
        em.clear();var stats=em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();boolean enabled=stats.isStatisticsEnabled();stats.setStatisticsEnabled(true);stats.clear();
        try{var d=read(u,"/"+id+"/payments?page=2&size=7");assertThat(d.path("total").asInt()).isEqualTo(151);assertThat(d.path("records").size()).isEqualTo(7);assertThat(d.path("records").get(0).path("paymentTransactionId").asLong()).isEqualTo(ids.get(7));assertThat(d.path("records").get(6).path("paymentTransactionId").asLong()).isEqualTo(ids.get(13));assertThat(stats.getEntityStatistics("com.gym.fitness.module.commercial.entity.PaymentTransactionEntity").getLoadCount()).isEqualTo(7);}finally{stats.setStatisticsEnabled(enabled);}
        var all=read(u,"/"+id);assertThat(all.path("preview").path("paymentCount").asInt()).isEqualTo(151);assertThat(all.path("preview").path("systemReceipts").decimalValue()).isEqualByComparingTo("151");var request=submit(all,"151","0","0","0",null);payment(o,"PAYMENT","CASH","2");write(u,"/"+id+"/submit",request,2600);all=read(u,"/"+id);var closed=write(u,"/"+id+"/submit",submit(all,"153","0","0","0",null),0);assertThat(closed.path("paymentTotal").asInt()).isEqualTo(152);
    }
    @Test void nullOnlyChannelsAreParameterErrorsWithoutClosingShift() throws Exception {nullChannelElements(false);}
    @Test void nullAfterValidChannelsIsParameterErrorWithoutClosingShift() throws Exception {nullChannelElements(true);}
    private void nullChannelElements(boolean afterValid)throws Exception {
        var u=account("RECEPTION",0);long id=open(u);var d=read(u,"/"+id);var request=zeroSubmit(d);
        var rows=afterValid?(List<Object>)request.get("channels"):new ArrayList<Object>();rows.add(null);request.put("channels",rows);
        var response=mvc.perform(post("/api/v1/cash-shifts/"+id+"/submit").header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request))).andReturn().getResponse();
        assertThat(response.getStatus()).isIn(200,400);assertThat(json.readTree(response.getContentAsString()).path("code").asInt()).isEqualTo(1001);
        d=read(u,"/"+id);assertThat(d.path("shift").path("status").asText()).isEqualTo("OPEN");assertThat(d.path("shift").path("version").asLong()).isZero();assertThat(d.path("audit").size()).isEqualTo(1);
    }
    String key(){return UUID.randomUUID().toString();}
    SysUserEntity account(String role,int i){var v=venues.findAll().get(i);var r=roles.findByCode(role).orElseThrow();var u=new SysUserEntity();u.setUsername("cash-"+key());u.setRealName(u.getUsername());u.setPasswordHash("unused");u.setRoleId(r.getId());u.setRole(r);u.setVenueId(v.getId());u.setVenue(v);u.setStatus("ACTIVE");u.setMustChangePassword(false);return users.saveAndFlush(u);}
    String token(SysUserEntity u){return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole().getCode(),u.getVenueId(),u.currentSessionVersion());}
    JsonNode write(SysUserEntity u,String path,Object body,int code)throws Exception{return json.readTree(mvc.perform(post("/api/v1/cash-shifts"+path).header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
    JsonNode read(SysUserEntity u,String path)throws Exception{return readCode(u,path,0);}
    JsonNode readCode(SysUserEntity u,String path,int code)throws Exception{return json.readTree(mvc.perform(get("/api/v1/cash-shifts"+path).header("Authorization",token(u))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
}
