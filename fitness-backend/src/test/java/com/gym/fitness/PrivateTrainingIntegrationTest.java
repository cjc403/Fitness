package com.gym.fitness;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.commercial.entity.*;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.course.entity.*;
import com.gym.fitness.module.course.repository.*;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.training.repository.*;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:private_training_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE", "spring.jpa.hibernate.ddl-auto=create-drop", "app.ai.enabled=false"})
@AutoConfigureMockMvc
class PrivateTrainingIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgres(org.springframework.test.context.DynamicPropertyRegistry properties) {
        String url=System.getenv("FITCHAIN_TEST_DB_URL");
        if(url==null)return;
        if(!"jdbc:postgresql://127.0.0.1:15432/fitchain_test_pt_20260917".equals(url))throw new IllegalArgumentException("PostgreSQL destructive schema tests require the exact dedicated disposable database");
        properties.add("spring.datasource.url",()->url);
        properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        properties.add("spring.datasource.username",()->"fitchain_pt_test");
        properties.add("spring.datasource.password",()->System.getenv("FITCHAIN_TEST_DB_PASSWORD"));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SysUserRepository users;
    @Autowired MemberRepository members;
    @Autowired CourseRepository courses;
    @Autowired CourseScheduleRepository schedules;
    @Autowired BookingRepository bookings;
    @Autowired MembershipProductRepository products;
    @Autowired SalesOrderRepository orders;
    @Autowired MemberEntitlementRepository entitlements;
    @Autowired JwtTokenProvider jwt;
    @Autowired PrivateLessonRepository lessons;
    @Autowired CoachSettlementRepository settlements;
    @Autowired CommissionRuleRepository rules;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired com.gym.fitness.module.organization.repository.RoleRepository roleRepository;
    @Autowired com.gym.fitness.module.organization.repository.VenueRepository venueRepository;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    Long memberId, venueId, coachId, entitlementId, scheduleId;

    @BeforeEach void fixture() {
        lessons.deleteAll();settlements.deleteAll();rules.deleteAll();
        entitlements.findAll().forEach(e->{e.setReservedUses(0);e.setStatus("FROZEN");entitlements.save(e);});
        var memberUser=users.findByUsername("member").orElseThrow();
        var member=members.findByUserId(memberUser.getId()).orElseThrow();
        memberId=member.getId(); venueId=member.getVenueId(); coachId=users.findByUsername("coach").orElseThrow().getId();
        var p=new MembershipProductEntity(); p.setName("测试私教"+UUID.randomUUID()); p.setCategory("PACKAGE");p.setProductType("PRIVATE_PACKAGE");p.setValidityDays(90);p.setTotalUses(2);p.setPrice(new BigDecimal("200"));p.setAccessAllowed(false);p.setVenueId(venueId);products.save(p);
        var o=new SalesOrderEntity();o.setOrderNo(UUID.randomUUID().toString());o.setOrderType("NEW");o.setMemberId(memberId);o.setProductId(p.getId());o.setVenueId(venueId);o.setOriginalAmount(new BigDecimal("200"));o.setPaidAmount(new BigDecimal("200"));o.setStatus("PAID");orders.save(o);
        var e=new MemberEntitlementEntity();e.setEntitlementNo(UUID.randomUUID().toString());e.setMemberId(memberId);e.setProductId(p.getId());e.setSourceOrderId(o.getId());e.setVenueId(venueId);e.setValidFrom(LocalDate.now().minusDays(1));e.setValidUntil(LocalDate.now().plusDays(90));e.setTotalUses(2);e.setRemainingUses(2);e.setStatus("ACTIVE");entitlements.save(e);entitlementId=e.getId();
        var c=new CourseEntity();c.setName("测试私教");c.setType("PRIVATE");c.setVenueId(venueId);c.setMaxCapacity(1);c.setFee(new BigDecimal("999"));c.setStatus("ACTIVE");courses.save(c);
        var s=new CourseScheduleEntity();s.setCourseId(c.getId());s.setCoachId(coachId);s.setCoachName("测试教练");s.setVenueId(venueId);s.setScheduledStartTime(LocalDateTime.now().plusDays(1));s.setScheduledEndTime(LocalDateTime.now().plusDays(1).plusHours(1));s.setMaxStudents(1);s.setEnrolledCount(0);s.setStatus("SCHEDULED");schedules.save(s);scheduleId=s.getId();
    }
    String token(String name) {var u=users.findByUsername(name).orElseThrow();return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole().getCode(),u.getVenueId(),u.currentSessionVersion());}
    JsonNode postJson(String path,String who,Object body) throws Exception {return json.readTree(mvc.perform(post(path).header("Authorization",token(who)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
    JsonNode success(String path,String who,Object body) throws Exception {var r=postJson(path,who,body);assertEquals(0,r.path("code").asInt(),r.toString());return r.path("data");}
    long reserve() throws Exception {return success("/api/v1/admin/bookings","admin",Map.of("scheduleId",scheduleId,"memberId",memberId,"entitlementId",entitlementId)).path("id").asLong();}
    void past() {var s=schedules.findById(scheduleId).orElseThrow();s.setScheduledStartTime(LocalDateTime.now().minusHours(2));s.setScheduledEndTime(LocalDateTime.now().minusHours(1));schedules.save(s);}
    long complete() throws Exception {long id=reserve();past();success("/api/v1/admin/bookings/"+id+"/check-in","coach",Map.of());success("/api/v1/training/lessons/"+id+"/submit","coach",Map.of());success("/api/v1/training/lessons/"+id+"/confirm","member",Map.of());return id;}

    @Test void privateReservationRequiresOwnValidPackage() throws Exception {
        var e=entitlements.findById(entitlementId).orElseThrow();e.setStatus("FROZEN");entitlements.save(e);
        assertNotEquals(0,postJson("/api/v1/admin/bookings","admin",Map.of("scheduleId",scheduleId,"memberId",memberId,"entitlementId",entitlementId)).path("code").asInt());
        assertTrue(bookings.findByScheduleIdAndMemberId(scheduleId,memberId).isEmpty());
    }
    @Test void reservationIsReleasedWithoutConsumptionOnCancellation() throws Exception {
        long id=reserve();mvc.perform(get("/api/v1/commercial/me/entitlements").header("Authorization",token("member"))).andExpect(jsonPath("$.data[?(@.id=="+entitlementId+")].reservedUses").value(org.hamcrest.Matchers.contains(1)));
        assertEquals(2,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
        mvc.perform(put("/api/v1/member/bookings/"+id+"/cancel").header("Authorization",token("member"))).andExpect(jsonPath("$.code").value(0));
        assertEquals(2,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
        mvc.perform(get("/api/v1/training/lessons").header("Authorization",token("member"))).andExpect(jsonPath("$.data[?(@.bookingId=="+id+")].status").value(org.hamcrest.Matchers.contains("RELEASED")));
    }
    @Test void confirmationConsumesOnceAndLocksFinancialSnapshots() throws Exception {
        mvc.perform(put("/api/v1/training/commission-rules").header("Authorization",token("manager")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("coachId",coachId,"rate",20)))).andExpect(jsonPath("$.code").value(0));
        long id=reserve();assertNotEquals(0,postJson("/api/v1/training/lessons/"+id+"/submit","coach",Map.of()).path("code").asInt());
        past();success("/api/v1/admin/bookings/"+id+"/check-in","coach",Map.of());assertEquals(2,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
        mvc.perform(get("/api/v1/coach/workbench").header("Authorization",token("coach"))).andExpect(jsonPath("$.data.summary.serviceRevenue").value(0));
        success("/api/v1/training/lessons/"+id+"/submit","coach",Map.of());var result=success("/api/v1/training/lessons/"+id+"/confirm","member",Map.of());assertEquals("CONFIRMED",result.path("status").asText());assertEquals(100,result.path("unitPrice").asInt());assertTrue(result.path("commissionAmount").isNull());
        mvc.perform(get("/api/v1/training/lessons").header("Authorization",token("coach"))).andExpect(jsonPath("$.data[?(@.bookingId=="+id+")].commissionAmount").value(org.hamcrest.Matchers.contains(20.0)));
        mvc.perform(get("/api/v1/coach/workbench").header("Authorization",token("coach"))).andExpect(jsonPath("$.data.summary.serviceRevenue").value(100)).andExpect(jsonPath("$.data.summary.estimatedCommission").value(20));
        success("/api/v1/training/lessons/"+id+"/confirm","member",Map.of());assertEquals(1,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
        var sheet=success("/api/v1/training/settlements","manager",Map.of("coachId",coachId,"month",YearMonth.now().toString()));long sheetId=sheet.path("id").asLong();assertEquals(20,sheet.path("commissionAmount").asInt());
        assertEquals(sheetId,success("/api/v1/training/settlements","manager",Map.of("coachId",coachId,"month",YearMonth.now().toString())).path("id").asLong());
        assertNotEquals(0,postJson("/api/v1/training/settlements/"+sheetId+"/approve","manager",Map.of()).path("code").asInt());
        assertNotEquals(0,postJson("/api/v1/training/lessons/"+id+"/reverse","manager",Map.of("reason","录入纠错")).path("code").asInt());
    }
    @Test void pendingPackageCannotBeTransferredRefundedOrFrozen() throws Exception {
        long id=reserve();assertNotEquals(0,postJson("/api/v1/commercial/entitlements/"+entitlementId+"/freeze","admin",Map.of("note","测试")).path("code").asInt());
        assertNotEquals(0,postJson("/api/v1/commercial/entitlements/"+entitlementId+"/refund","admin",Map.of("amount",100,"paymentMethod","CASH")).path("code").asInt());
        var target=members.findAll().stream().filter(m->m.getVenueId().equals(venueId)&&!m.getId().equals(memberId)).findFirst().orElseThrow();
        assertNotEquals(0,postJson("/api/v1/commercial/entitlements/"+entitlementId+"/transfer","admin",Map.of("targetMemberId",target.getId())).path("code").asInt());assertEquals("BOOKED",bookings.findById(id).orElseThrow().getStatus());
    }
    @Test void unsettledCorrectionRestoresCreditAndCannotBeRepeated() throws Exception {
        long id=complete();success("/api/v1/training/lessons/"+id+"/reverse","manager",Map.of("reason","会员反馈，核对纠错"));assertEquals(2,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
        success("/api/v1/training/lessons/"+id+"/reverse","manager",Map.of("reason","重试"));assertEquals(2,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
    }
    @Test void lastCreditCannotBeReservedByTwoConcurrentBookings() throws Exception {
        var e=entitlements.findById(entitlementId).orElseThrow();e.setRemainingUses(1);entitlements.save(e);
        var s=schedules.findById(scheduleId).orElseThrow();s.setId(null);s.setScheduledStartTime(s.getScheduledStartTime().plusHours(2));s.setScheduledEndTime(s.getScheduledEndTime().plusHours(2));schedules.save(s);long other=s.getId();
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {var tasks=new ArrayList<Future<Integer>>();for(long sid:List.of(scheduleId,other)) tasks.add(pool.submit(()->{start.await();return postJson("/api/v1/admin/bookings","admin",Map.of("scheduleId",sid,"memberId",memberId,"entitlementId",entitlementId)).path("code").asInt();}));start.countDown();int ok=0;for(var f:tasks)if(f.get(15,TimeUnit.SECONDS)==0)ok++;assertEquals(1,ok);} finally {pool.shutdownNow();}
        assertEquals(1,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
    }
    @Test void memberAndReceptionCannotApproveSettlements() throws Exception {
        for(String who:List.of("member","reception","coach"))mvc.perform(post("/api/v1/training/settlements/1/approve").header("Authorization",token(who))).andExpect(status().isForbidden());
    }
    @Test void renewalBetweenReservationAndSignatureUsesCurrentWeightedPrice() throws Exception {
        long id=reserve();
        success("/api/v1/commercial/entitlements/"+entitlementId+"/renew","admin",Map.of("idempotencyKey",UUID.randomUUID().toString(),"additionalUses",2,"amount",400,"paymentMethod","CASH"));
        past();success("/api/v1/admin/bookings/"+id+"/check-in","coach",Map.of());success("/api/v1/training/lessons/"+id+"/submit","coach",Map.of());
        assertEquals(150,success("/api/v1/training/lessons/"+id+"/confirm","member",Map.of()).path("unitPrice").asInt());
        assertEquals(3,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
    }
    @Test void draftIncludesLaterConfirmedLessonsAndPreviousMonthApprovalIsIdempotent() throws Exception {
        long first=complete();
        var sheet=success("/api/v1/training/settlements","manager",Map.of("coachId",coachId,"month",YearMonth.now().toString()));
        var original=schedules.findById(scheduleId).orElseThrow();original.setId(null);original.setEnrolledCount(0);original.setScheduledStartTime(LocalDateTime.now().plusDays(2));original.setScheduledEndTime(LocalDateTime.now().plusDays(2).plusHours(1));schedules.save(original);scheduleId=original.getId();
        complete();var refreshed=success("/api/v1/training/settlements","manager",Map.of("coachId",coachId,"month",YearMonth.now().toString()));assertEquals(sheet.path("id").asLong(),refreshed.path("id").asLong());assertEquals(2,refreshed.path("lessonCount").asInt());
        // Move this isolated fixture to a completed month; real signatures always use the current server time.
        var settlement=settlements.findById(sheet.path("id").asLong()).orElseThrow();settlement.setMonth(YearMonth.now().minusMonths(1).toString());settlements.save(settlement);
        success("/api/v1/training/settlements/"+settlement.getId()+"/approve","manager",Map.of());success("/api/v1/training/settlements/"+settlement.getId()+"/approve","manager",Map.of());assertEquals("APPROVED",settlements.findById(settlement.getId()).orElseThrow().getStatus());
        assertNotEquals(0,postJson("/api/v1/training/lessons/"+first+"/reverse","manager",Map.of("reason","已结算不允许")).path("code").asInt());
    }
    @Test void invalidPrivateWaitlistCannotRollBackAnotherMembersCancellation() throws Exception {
        long id=reserve();var target=members.findAll().stream().filter(m->m.getVenueId().equals(venueId)&&!m.getId().equals(memberId)).findFirst().orElseThrow();
        var candidate=new com.gym.fitness.module.booking.entity.BookingEntity();candidate.setMemberId(target.getId());candidate.setScheduleId(scheduleId);candidate.setStatus("WAITLISTED");candidate.setWaitlistPosition(1);candidate.setBookedAt(LocalDateTime.now());bookings.save(candidate);
        mvc.perform(put("/api/v1/member/bookings/"+id+"/cancel").header("Authorization",token("member"))).andExpect(jsonPath("$.code").value(0));
        assertEquals("CANCELLED",bookings.findById(candidate.getId()).orElseThrow().getStatus());assertEquals(0,entitlements.findById(entitlementId).orElseThrow().getReservedUses());
    }
    @Test void entranceNeverConsumesPrivateLessonUsesEvenIfProductAllowsAccess() throws Exception {
        var m=new MemberEntity();m.setMemberNo(UUID.randomUUID().toString());m.setRealName("入场测试");m.setPhone("13900000000");m.setVenueId(venueId);m.setStatus("ACTIVE");m.setBlacklistFlag(false);m.setAccessToken(UUID.randomUUID().toString());members.save(m);
        var e=entitlements.findById(entitlementId).orElseThrow();e.setMemberId(m.getId());entitlements.save(e);var p=products.findById(e.getProductId()).orElseThrow();p.setAccessAllowed(true);products.save(p);
        success("/api/v1/commercial/access/scan","admin",Map.of("token",m.getAccessToken(),"direction","IN"));assertEquals(2,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
    }
    @Test void noShowReleasesReservationWithoutConsumption() throws Exception {
        long id=reserve();past();success("/api/v1/admin/bookings/"+id+"/no-show","coach",Map.of());var e=entitlements.findById(entitlementId).orElseThrow();assertEquals(0,e.getReservedUses());assertEquals(2,e.getRemainingUses());
    }
    @Test void automaticSelectionCannotDoubleReserveTheLastLesson() throws Exception {
        var e=entitlements.findById(entitlementId).orElseThrow();e.setRemainingUses(1);entitlements.save(e);
        var s=schedules.findById(scheduleId).orElseThrow();s.setId(null);s.setScheduledStartTime(s.getScheduledStartTime().plusHours(2));s.setScheduledEndTime(s.getScheduledEndTime().plusHours(2));schedules.save(s);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);try {var tasks=new ArrayList<Future<Integer>>();for(long sid:List.of(scheduleId,s.getId()))tasks.add(pool.submit(()->{start.await();return postJson("/api/v1/admin/bookings","admin",Map.of("scheduleId",sid,"memberId",memberId)).path("code").asInt();}));start.countDown();int ok=0;for(var f:tasks)if(f.get(15,TimeUnit.SECONDS)==0)ok++;assertEquals(1,ok);assertEquals(1,entitlements.findById(entitlementId).orElseThrow().getReservedUses());}finally{pool.shutdownNow();}
    }
    @Test void settlementBindingWhileCorrectionWaitsCannotBeOverwritten() throws Exception {
        long id=complete();var pool=Executors.newSingleThreadExecutor();var correctionThread=new java.util.concurrent.atomic.AtomicReference<Thread>();var future=new java.util.concurrent.atomic.AtomicReference<Future<JsonNode>>();
        try {
            transactions.executeWithoutResult(tx->{
                users.findByIdForUpdate(coachId).orElseThrow();
                future.set(pool.submit(()->{correctionThread.set(Thread.currentThread());return postJson("/api/v1/training/lessons/"+id+"/reverse","manager",Map.of("reason","并发纠错"));}));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                while(System.nanoTime()<deadline){var thread=correctionThread.get();if(thread!=null&&Arrays.stream(thread.getStackTrace()).anyMatch(f->f.getClassName().endsWith("TrainingService")&&f.getMethodName().equals("coachLock"))){waiting=true;break;}try{Thread.sleep(10);}catch(InterruptedException ex){throw new RuntimeException(ex);}}
                assertTrue(waiting,"Correction must be waiting for the actual coach lock before binding");
                var sheet=new com.gym.fitness.module.training.entity.CoachSettlementEntity();sheet.setCoachId(coachId);sheet.setVenueId(venueId);sheet.setMonth(YearMonth.now().toString());sheet.setLessonCount(1);sheet.setServiceAmount(new BigDecimal("100"));sheet.setCommissionAmount(new BigDecimal("40"));sheet.setStatus("DRAFT");sheet.setCreatedBy(users.findByUsername("admin").orElseThrow().getId());sheet.setCreatedAt(LocalDateTime.now());settlements.saveAndFlush(sheet);
                var lesson=lessons.findByBookingId(id).orElseThrow();lesson.setSettlementId(sheet.getId());lessons.saveAndFlush(lesson);
            });
            assertNotEquals(0,future.get().get(15,TimeUnit.SECONDS).path("code").asInt());assertEquals("CONFIRMED",lessons.findByBookingId(id).orElseThrow().getStatus());assertNotNull(lessons.findByBookingId(id).orElseThrow().getSettlementId());assertEquals(1,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
        } finally {pool.shutdownNow();}
    }
    @Test void anotherMembersPackageCannotBeUsedAndReceptionCannotSubmit() throws Exception {
        var e=entitlements.findById(entitlementId).orElseThrow();e.setMemberId(members.findAll().stream().filter(m->m.getVenueId().equals(venueId)&&!m.getId().equals(memberId)).findFirst().orElseThrow().getId());entitlements.save(e);
        assertNotEquals(0,postJson("/api/v1/admin/bookings","admin",Map.of("scheduleId",scheduleId,"memberId",memberId,"entitlementId",entitlementId)).path("code").asInt());assertEquals(0,entitlements.findById(entitlementId).orElseThrow().getReservedUses());
        mvc.perform(post("/api/v1/training/lessons/1/submit").header("Authorization",token("reception"))).andExpect(status().isForbidden());
    }
    @Test void productionRestartNeverResetsAnExistingAdministrator() {
        var before=users.findByUsername("admin").orElseThrow();String hash=before.getPasswordHash();
        var initializer=new com.gym.fitness.config.ProductionAdminInitializer(users,roleRepository,venueRepository,encoder);
        org.springframework.test.util.ReflectionTestUtils.setField(initializer,"adminPassword",UUID.randomUUID().toString());initializer.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
        assertEquals(hash,users.findByUsername("admin").orElseThrow().getPasswordHash());
    }
    @Test void correctionAfterRenewalRestoresTheOriginalValueIntoTheCreditPool() throws Exception {
        long first=complete();success("/api/v1/commercial/entitlements/"+entitlementId+"/renew","admin",Map.of("idempotencyKey",UUID.randomUUID().toString(),"additionalUses",2,"amount",400,"paymentMethod","CASH"));
        success("/api/v1/training/lessons/"+first+"/reverse","manager",Map.of("reason","续费后核对纠错"));
        assertEquals(new BigDecimal("150.00"),entitlements.findById(entitlementId).orElseThrow().getLessonUnitPrice());
        var s=schedules.findById(scheduleId).orElseThrow();s.setId(null);s.setEnrolledCount(0);s.setScheduledStartTime(LocalDateTime.now().plusDays(2));s.setScheduledEndTime(LocalDateTime.now().plusDays(2).plusHours(1));schedules.save(s);scheduleId=s.getId();
        long next=complete();assertEquals(new BigDecimal("150.00"),lessons.findByBookingId(next).orElseThrow().getUnitPrice());
    }
    @Test void paidPrivateRenewalCannotAddZeroLessons() throws Exception {
        long before=orders.count();assertNotEquals(0,postJson("/api/v1/commercial/entitlements/"+entitlementId+"/renew","admin",Map.of("idempotencyKey",UUID.randomUUID().toString(),"additionalUses",0,"amount",400,"paymentMethod","CASH")).path("code").asInt());assertEquals(before,orders.count());assertEquals(2,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
    }
    @Test void explicitlyDisabledMigrationSeedIsBootstrappedOnce() {
        var before=users.findByUsername("admin").orElseThrow();String hash=before.getPasswordHash();
        before.setStatus("INACTIVE");before.setPasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");users.save(before);
        var initializer=new com.gym.fitness.config.ProductionAdminInitializer(users,roleRepository,venueRepository,encoder);String bootstrap=UUID.randomUUID().toString();org.springframework.test.util.ReflectionTestUtils.setField(initializer,"adminPassword",bootstrap);
        initializer.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));var initialized=users.findByUsername("admin").orElseThrow();assertEquals("ACTIVE",initialized.getStatus());assertTrue(encoder.matches(bootstrap,initialized.getPasswordHash()));
        initialized.setPasswordHash(hash);users.save(initialized);
    }
    @Test void memberCannotReadEmployeeCommissionAndOtherVenueManagerCannotReverse() throws Exception {
        long id=complete();
        mvc.perform(get("/api/v1/training/lessons").header("Authorization",token("member"))).andExpect(jsonPath("$.data[?(@.bookingId=="+id+")].commissionAmount").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
        var base=users.findByUsername("manager").orElseThrow();var u=new com.gym.fitness.module.auth.entity.SysUserEntity();u.setUsername("scope"+UUID.randomUUID());u.setPasswordHash(base.getPasswordHash());u.setRoleId(base.getRoleId());u.setVenueId(venueRepository.findAll().stream().filter(v->!v.getId().equals(venueId)).findFirst().orElseThrow().getId());u.setStatus("ACTIVE");u.setRealName("隔离验证");users.save(u);
        assertNotEquals(0,postJson("/api/v1/training/lessons/"+id+"/reverse",u.getUsername(),Map.of("reason","其他门店拒绝")).path("code").asInt());assertEquals(1,entitlements.findById(entitlementId).orElseThrow().getRemainingUses());
    }
}
