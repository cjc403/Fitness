package com.gym.fitness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.commercial.entity.*;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.member.entity.*;
import com.gym.fitness.module.member.repository.*;
import com.gym.fitness.module.organization.repository.*;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:retention;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.ai.enabled=false", "fitness.operations.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class RetentionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SysUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired VenueRepository venues;
    @Autowired MemberRepository members;
    @Autowired MemberFollowUpRepository followUps;
    @Autowired MemberEntitlementRepository entitlements;
    @Autowired SalesOrderRepository orders;
    @Autowired MembershipProductRepository products;
    @Autowired JwtTokenProvider jwt;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired com.gym.fitness.module.member.service.AdvisorService advisorService;
    @Autowired com.gym.fitness.module.member.service.RetentionService retentionService;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test void advisorFiltersUseCalendarDaysAndKeepOwnershipAndVenueBoundaries() throws Exception {
        var sales = account("SALES", 0); var other = account("SALES", 0);
        var today = member(sales, 0); var overdue = member(sales, 0);
        member(other, 0); member(sales, 1); // even an inconsistent historical assignment must not leak another venue
        card(today, 7, "ACTIVE"); card(overdue, 8, "ACTIVE"); card(overdue, -1, "ACTIVE");
        follow(today, sales, LocalDate.now().atTime(23, 59));
        follow(overdue, sales, LocalDate.now().minusDays(1).atTime(12, 0));
        var all = retention(sales, "ALL");
        assertThat(all.path("summary").path("totalMembers").asInt()).isEqualTo(2);
        assertThat(all.path("summary").path("todayFollowUps").asInt()).isEqualTo(1);
        assertThat(all.path("summary").path("overdueFollowUps").asInt()).isEqualTo(1);
        assertThat(all.path("summary").path("expiring7").asInt()).isEqualTo(1);
        assertThat(all.path("summary").path("expiring30").asInt()).isEqualTo(2);
        assertThat(all.path("advisors").size()).isZero();
        assertThat(retention(sales, "TODAY").path("members").get(0).path("memberId").asLong()).isEqualTo(today.getId());
        assertThat(retention(sales, "OVERDUE").path("members").get(0).path("memberId").asLong()).isEqualTo(overdue.getId());
        assertThat(retention(sales, "EXPIRING_7").path("members").size()).isEqualTo(1);
        assertThat(retention(sales, "EXPIRING_30").path("members").size()).isEqualTo(2);
    }

    @Test void managerAndHeadquartersReadOnlyTheirPermittedScope() throws Exception {
        var salesA = account("SALES", 0); var salesB = account("SALES", 1);
        var a = member(salesA, 0); var b = member(salesB, 1);
        var manager = account("MANAGER", 0); var admin = account("SUPER_ADMIN", 0);
        var own = retention(manager, "ALL");
        for (var row : own.path("members")) assertThat(row.path("venueId").asLong()).isEqualTo(manager.getVenueId());
        assertThat(own.path("advisors").toString()).contains(salesA.getRealName()).doesNotContain(salesB.getRealName());
        var global = retention(admin, "ALL");
        assertThat(global.path("members").toString()).contains(a.getMemberNo()).contains(b.getMemberNo());
        for (var role : List.of("RECEPTION", "COACH", "MEMBER")) {
            mvc.perform(get("/api/v1/admin/advisor/retention").header("Authorization", token(account(role, 0))))
                .andExpect(status().isForbidden());
        }
    }

    @Test void followUpResultsAreStoredWithoutManuallyInventingRenewals() throws Exception {
        var sales = account("SALES", 0); var m = member(sales, 0);
        String body = "{\"followType\":\"CALL\",\"content\":\"讨论续费\",\"outcome\":\"RENEWAL_PENDING\",\"nextFollowAt\":\"" + LocalDateTime.now().plusDays(1).withNano(0) + "\"}";
        postFollow(sales, m, body, 0).andExpect(jsonPath("$.data.outcome").value("RENEWAL_PENDING"));
        var view = retention(sales, "ALL");
        assertThat(view.path("summary").path("followedThisMonth").asInt()).isEqualTo(1);
        assertThat(view.path("summary").path("renewedThisMonth").asInt()).isZero();
        assertThat(view.path("members").get(0).path("lastOutcome").asText()).isEqualTo("RENEWAL_PENDING");
        postFollow(sales, m, "{\"followType\":\"CALL\",\"content\":\"伪造成交\",\"outcome\":\"RENEWED\"}", 1001);
        mvc.perform(post("/api/v1/admin/members/{id}/follow-ups", m.getId()).header("Authorization", token(sales))
            .contentType(MediaType.APPLICATION_JSON).content("{\"followType\":\"CALL\",\"content\":\"   \"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        postFollow(sales, m, "{\"followType\":\"CALL\",\"content\":\"过去日期\",\"nextFollowAt\":\"2020-01-01T12:00:00\"}", 1001);
    }

    @Test void historicalClientsRemainCompatibleAndOtherAdvisorsCannotReadOrWriteNotes() throws Exception {
        var sales = account("SALES", 0); var other = account("SALES", 0); var m = member(sales, 0);
        postFollow(sales, m, "{\"followType\":\"WECHAT\",\"content\":\"旧客户端记录\"}", 0)
            .andExpect(jsonPath("$.data.outcome").value("NOT_RECORDED"));
        postFollow(other, m, "{\"followType\":\"CALL\",\"content\":\"越权\"}", 1003);
        mvc.perform(get("/api/v1/admin/members/{id}/follow-ups", m.getId()).header("Authorization", token(other)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1003));
        mvc.perform(get("/api/v1/admin/members/{id}/follow-ups", m.getId()).header("Authorization", token(account("MANAGER", 1))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1003));
    }

    @Test void anIdenticalRetryReturnsOneRecordAndChangedContentIsRejected() throws Exception {
        var sales = account("SALES", 0); var m = member(sales, 0);
        String body = "{\"followType\":\"CALL\",\"content\":\"等待答复\",\"outcome\":\"NO_CONTACT\",\"idempotencyKey\":\"same-intent\"}";
        var first = mapper.readTree(postFollow(sales, m, body, 0).andReturn().getResponse().getContentAsString()).path("data").path("id");
        var repeat = mapper.readTree(postFollow(sales, m, body, 0).andReturn().getResponse().getContentAsString()).path("data").path("id");
        assertThat(repeat).isEqualTo(first);
        assertThat(followUps.findByMemberIdOrderByIdDesc(m.getId())).hasSize(1);
        postFollow(sales, m, body.replace("等待答复", "不同内容"), 1001);
    }

    @Test void aSalesUserCannotUseAHistoricalCoachAssignmentToReadOrWriteAnotherAdvisorsNotes() throws Exception {
        var sales = account("SALES", 0); var other = account("SALES", 0); var m = member(sales, 0);
        m.setCoachId(other.getId()); members.saveAndFlush(m);
        follow(m, sales, null);
        mvc.perform(get("/api/v1/admin/members/{id}/follow-ups", m.getId()).header("Authorization", token(other)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1003));
        postFollow(other, m, "{\"followType\":\"CALL\",\"content\":\"历史教练归属越权\"}", 1003);
    }

    @Test void identicalRetryStillReplaysAfterTheScheduledContactTimeHasPassed() throws Exception {
        var sales = account("SALES", 0); var m = member(sales, 0);
        Clock original = (Clock) ReflectionTestUtils.getField(advisorService, "contactClock");
        Instant firstTime = Instant.now();
        LocalDateTime next = LocalDateTime.ofInstant(firstTime.plusSeconds(60), ZoneId.of("Asia/Shanghai")).withNano(0);
        String body = mapper.writeValueAsString(Map.of("followType", "CALL", "content", "响应丢失后的原样重试",
            "idempotencyKey", "lost-response", "nextFollowAt", next.toString()));
        try {
            ReflectionTestUtils.setField(advisorService, "contactClock", Clock.fixed(firstTime, original.getZone()));
            var first = mapper.readTree(postFollow(sales, m, body, 0).andReturn().getResponse().getContentAsString()).path("data").path("id");
            ReflectionTestUtils.setField(advisorService, "contactClock", Clock.fixed(firstTime.plusSeconds(120), original.getZone()));
            var replay = mapper.readTree(postFollow(sales, m, body, 0).andReturn().getResponse().getContentAsString()).path("data").path("id");
            assertThat(replay).isEqualTo(first);
            assertThat(followUps.findByMemberIdOrderByIdDesc(m.getId())).hasSize(1);
            postFollow(sales, m, body.replace("lost-response", "new-intent"), 1001);
        } finally {
            ReflectionTestUtils.setField(advisorService, "contactClock", original);
        }
    }

    @Test void contactCalendarUsesShanghaiEvenWhenServerDefaultTimezoneIsUtc() throws Exception {
        var sales = account("SALES", 0); var today = member(sales, 0); var overdue = member(sales, 0);
        Clock original = (Clock) ReflectionTestUtils.getField(retentionService, "businessClock");
        TimeZone originalZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertThat(original.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
            assertThat(((Clock) ReflectionTestUtils.getField(advisorService, "contactClock")).getZone()).isEqualTo(original.getZone());
            ReflectionTestUtils.setField(retentionService, "businessClock",
                Clock.fixed(Instant.parse("2026-10-10T16:30:00Z"), original.getZone()));
            follow(today, sales, LocalDateTime.parse("2026-10-11T18:00:00"));
            follow(overdue, sales, LocalDateTime.parse("2026-10-10T23:59:00"));
            var view = retention(sales, "ALL");
            assertThat(view.path("asOf").asText()).isEqualTo("2026-10-11T00:30:00");
            assertThat(retention(sales, "TODAY").path("members").get(0).path("memberId").asLong()).isEqualTo(today.getId());
            assertThat(retention(sales, "OVERDUE").path("members").get(0).path("memberId").asLong()).isEqualTo(overdue.getId());
        } finally {
            ReflectionTestUtils.setField(retentionService, "businessClock", original);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void simultaneousRequestsInSeparateTransactionsCreateOnlyOneFollowUp() throws Exception {
        var tx = new TransactionTemplate(transactions);
        var fixture = tx.execute(status -> {
            var sales = account("SALES", 0); return List.of(sales, member(sales, 0), token(sales));
        });
        var sales = (SysUserEntity) fixture.get(0); var m = (MemberEntity) fixture.get(1); var authorization = (String) fixture.get(2);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2); var go = new java.util.concurrent.CountDownLatch(1);
        String body = "{\"followType\":\"CALL\",\"content\":\"并发重试\",\"idempotencyKey\":\"concurrent-intent\"}";
        java.util.concurrent.Callable<Long> submit = () -> {
            ready.countDown(); assertThat(go.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var response = mvc.perform(post("/api/v1/admin/members/{id}/follow-ups", m.getId())
                .header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse();
            return mapper.readTree(response.getContentAsString()).path("data").path("id").asLong();
        };
        try {
            var first = pool.submit(submit); var second = pool.submit(submit);
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(followUps.findByMemberIdOrderByIdDesc(m.getId())).hasSize(1);
        } finally {
            go.countDown(); pool.shutdownNow(); pool.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS);
            tx.executeWithoutResult(status -> {
                followUps.deleteAll(followUps.findByMemberIdOrderByIdDesc(m.getId()));
                members.deleteById(m.getId()); users.deleteById(sales.getId());
            });
        }
    }

    @Test void renewalCountsComeFromNetPaidRenewalOrdersAndCountMembersOnce() throws Exception {
        var sales = account("SALES", 0); var a = member(sales, 0); var b = member(sales, 0);
        order(a, "RENEW", "PAID", "100", "0", LocalDateTime.now());
        order(a, "RENEW", "PARTIALLY_REFUNDED", "100", "40", LocalDateTime.now());
        order(b, "RENEW", "REFUNDED", "100", "100", LocalDateTime.now());
        order(b, "SALE", "PAID", "200", "0", LocalDateTime.now());
        order(b, "RENEW", "PAID", "200", "0", LocalDate.now().withDayOfMonth(1).minusDays(1).atTime(12, 0));
        var view = retention(sales, "ALL");
        assertThat(view.path("summary").path("renewedThisMonth").asInt()).isEqualTo(1);
        assertThat(view.path("summary").path("renewalNetAmount").decimalValue()).isEqualByComparingTo("160");
        var manager = account("MANAGER", 0);
        assertThat(retention(manager, "ALL").path("advisors").toString()).contains(sales.getRealName());
    }

    @Test void onlyLatestFollowUpDeterminesNextContactAndInvalidFilterIsRejected() throws Exception {
        var sales = account("SALES", 0); var m = member(sales, 0);
        follow(m, sales, LocalDate.now().minusDays(5).atStartOfDay());
        postFollow(sales, m, "{\"followType\":\"CALL\",\"content\":\"本次已完成，无后续安排\",\"outcome\":\"DECLINED\"}", 0);
        assertThat(retention(sales, "OVERDUE").path("members").size()).isZero();
        assertThat(retention(sales, "ALL").path("members").get(0).path("followStatus").asText()).isEqualTo("UNSCHEDULED");
        mvc.perform(get("/api/v1/admin/advisor/retention").param("filter", "INVALID").header("Authorization", token(sales)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1001));
    }

    @Test void highRiskAndKeywordFiltersDoNotChangeTheScopedSummary() throws Exception {
        var sales = account("SALES", 0); var m = member(sales, 0); var recent = member(sales, 0);
        jdbc.update("update member set created_at=? where id=?", java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(70)), m.getId());
        entityManager.clear();
        var high = retention(sales, "HIGH_RISK");
        assertThat(high.path("members").size()).isEqualTo(1);
        assertThat(high.path("members").get(0).path("memberId").asLong()).isEqualTo(m.getId());
        assertThat(high.path("members").get(0).path("riskReason").asText()).contains("从未到店");
        var searched = mapper.readTree(mvc.perform(get("/api/v1/admin/advisor/retention")
            .param("keyword", recent.getMemberNo()).header("Authorization", token(sales)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(searched.path("members").size()).isEqualTo(1);
        assertThat(searched.path("summary").path("totalMembers").asInt()).isEqualTo(2);
    }

    @Test void otherVenueRenewalsDoNotExposeAmountsToLocalStaff() throws Exception {
        Clock original = (Clock) ReflectionTestUtils.getField(retentionService, "businessClock");
        // Keep this authorization test independent of timestamp(6) rounding at "now".
        LocalDateTime asOf = LocalDateTime.of(2026, 10, 9, 12, 0);
        try {
            ReflectionTestUtils.setField(retentionService, "businessClock",
                Clock.fixed(asOf.atZone(original.getZone()).toInstant(), original.getZone()));
            var sales = account("SALES", 0); var m = member(sales, 0);
            var foreign = order(m, "RENEW", "PAID", "888", "0", asOf.minusMinutes(1));
            foreign.setVenueId(venues.findAll().get(1).getId());
            foreign.setProductId(products.findAll().stream().filter(p -> p.getVenueId().equals(foreign.getVenueId())).findFirst().orElseThrow().getId());
            orders.saveAndFlush(foreign);
            assertThat(retention(sales, "ALL").path("summary").path("renewalNetAmount").decimalValue()).isEqualByComparingTo("0");
            var global = retention(account("SUPER_ADMIN", 0), "ALL");
            var row = java.util.stream.StreamSupport.stream(global.path("members").spliterator(), false)
                .filter(value -> value.path("memberId").asLong() == m.getId()).findFirst().orElseThrow();
            assertThat(row.path("renewalNetAmount").decimalValue()).isEqualByComparingTo("888");
        } finally {
            ReflectionTestUtils.setField(retentionService, "businessClock", original);
        }
    }

    private JsonNode retention(SysUserEntity user, String filter) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/admin/advisor/retention").param("filter", filter)
            .header("Authorization", token(user))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
            .andReturn().getResponse().getContentAsString()).path("data");
    }
    private org.springframework.test.web.servlet.ResultActions postFollow(SysUserEntity u, MemberEntity m, String json, int code) throws Exception {
        return mvc.perform(post("/api/v1/admin/members/{id}/follow-ups", m.getId()).header("Authorization", token(u))
            .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
    }
    private SysUserEntity account(String code, int index) {
        var u = new SysUserEntity(); var role = roles.findByCode(code).orElseThrow(); var venue = venues.findAll().get(index);
        u.setUsername("ret-" + UUID.randomUUID()); u.setPasswordHash("unused-test-hash"); u.setRealName(u.getUsername());
        u.setRoleId(role.getId()); u.setRole(role); u.setVenueId(venue.getId()); u.setVenue(venue);
        u.setStatus("ACTIVE"); u.setMustChangePassword(false); u.setCoach(false); return users.saveAndFlush(u);
    }
    private MemberEntity member(SysUserEntity owner, int index) {
        var m = new MemberEntity(); m.setMemberNo("RET-" + UUID.randomUUID()); m.setRealName(m.getMemberNo());
        m.setPhone("13800001111"); m.setVenueId(venues.findAll().get(index).getId()); m.setSalesUserId(owner.getId());
        m.setStatus("ACTIVE"); m.setBlacklistFlag(false); return members.saveAndFlush(m);
    }
    private SalesOrderEntity order(MemberEntity m, String type, String status, String paid, String refunded, LocalDateTime paidAt) {
        var product = products.findAll().stream().filter(p -> p.getVenueId().equals(m.getVenueId())).findFirst().orElseThrow();
        var o = new SalesOrderEntity(); o.setOrderNo("RET-" + UUID.randomUUID()); o.setOrderType(type);
        o.setMemberId(m.getId()); o.setProductId(product.getId()); o.setVenueId(m.getVenueId());
        o.setOriginalAmount(new BigDecimal(paid)); o.setPaidAmount(new BigDecimal(paid)); o.setRefundedAmount(new BigDecimal(refunded));
        o.setStatus(status); o.setPaidAt(paidAt); o.setOperatorId(m.getSalesUserId()); return orders.saveAndFlush(o);
    }
    private void card(MemberEntity m, int days, String status) {
        var o = order(m, "SALE", "PAID", "100", "0", LocalDateTime.now());
        var e = new MemberEntitlementEntity(); e.setEntitlementNo("RET-" + UUID.randomUUID()); e.setMemberId(m.getId());
        e.setProductId(o.getProductId()); e.setSourceOrderId(o.getId()); e.setVenueId(m.getVenueId());
        e.setValidFrom(LocalDate.now().minusDays(30)); e.setValidUntil(LocalDate.now().plusDays(days)); e.setStatus(status);
        entitlements.saveAndFlush(e);
    }
    private void follow(MemberEntity m, SysUserEntity owner, LocalDateTime next) {
        var f = new MemberFollowUpEntity(); f.setMemberId(m.getId()); f.setStaffUserId(owner.getId()); f.setVenueId(m.getVenueId());
        f.setFollowType("CALL"); f.setContent("历史跟进"); f.setNextFollowAt(next); followUps.saveAndFlush(f);
    }
    private String token(SysUserEntity u) {
        return "Bearer " + jwt.generateToken(u.getId(), u.getUsername(), u.getRole().getCode(), u.getVenueId(), u.currentSessionVersion());
    }
}
