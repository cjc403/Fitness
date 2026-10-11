package com.gym.fitness;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.booking.entity.BookingEntity;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.commercial.entity.*;
import com.gym.fitness.module.commercial.repository.*;
import com.gym.fitness.module.course.entity.*;
import com.gym.fitness.module.course.repository.*;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import com.gym.fitness.module.training.repository.PrivateLessonRepository;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:commercial_boundary_20261002;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE;LOCK_TIMEOUT=10000", "spring.jpa.hibernate.ddl-auto=create-drop", "app.ai.enabled=false"})
@AutoConfigureMockMvc
class CommercialBoundaryIntegrationTest {
    @org.springframework.boot.test.mock.mockito.SpyBean com.gym.fitness.module.finance.service.EntitlementValueService values;
    @org.springframework.boot.test.mock.mockito.SpyBean com.gym.fitness.module.training.service.TrainingService training;
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired SysUserRepository users;
    @Autowired MemberRepository members; @Autowired VenueRepository venues; @Autowired MembershipProductRepository products;
    @Autowired SalesOrderRepository orders; @Autowired MemberEntitlementRepository entitlements;
    @Autowired ProductVenueRepository scopes; @Autowired BookingRepository bookings; @Autowired CourseRepository courses;
    @Autowired CourseScheduleRepository schedules; @Autowired PrivateLessonRepository lessons; @Autowired JwtTokenProvider jwt;
    @Autowired PaymentTransactionRepository payments;
    @Autowired com.gym.fitness.module.coupon.repository.CouponTemplateRepository couponTemplates;
    @Autowired com.gym.fitness.module.coupon.repository.MemberCouponRepository coupons;
    @Autowired com.gym.fitness.module.finance.repository.EntitlementValueAccountRepository accounts;
    Long memberId,a,b,coachId; String coachB;
    @BeforeEach void fixture() {
        lessons.deleteAll(); bookings.deleteAll();
        accounts.findAll().forEach(v->{v.setActiveRefundId(null);accounts.save(v);});
        entitlements.findAll().forEach(e->{e.setStatus("FROZEN");e.setReservedUses(0);entitlements.save(e);});
        var member=members.findByUserId(users.findByUsername("member").orElseThrow().getId()).orElseThrow();
        memberId=member.getId();a=member.getVenueId();b=venues.findAll().stream().filter(v->!v.getId().equals(a)).findFirst().orElseThrow().getId();
        var receptionist=users.findByUsername("reception").orElseThrow();receptionist.setVenueId(a);users.save(receptionist);
        coachId=users.findByUsername("coach").orElseThrow().getId();
        coachB="boundaryCoach"+UUID.randomUUID().toString().substring(0,8);
        var template=users.findById(coachId).orElseThrow();var u=new SysUserEntity();u.setUsername(coachB);u.setPasswordHash(template.getPasswordHash());u.setRoleId(template.getRoleId());u.setVenueId(b);u.setRealName("跨店教练");u.setStatus("ACTIVE");users.save(u);
        var manager=users.findByUsername("manager").orElseThrow();var reviewer=new SysUserEntity();reviewer.setUsername("boundaryReviewer"+UUID.randomUUID().toString().substring(0,8));reviewer.setPasswordHash(manager.getPasswordHash());reviewer.setRoleId(manager.getRoleId());reviewer.setVenueId(b);reviewer.setStatus("ACTIVE");users.save(reviewer);
    }
    String token(String who){var u=users.findByUsername(who).orElseThrow();return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole().getCode(),u.getVenueId(),u.currentSessionVersion());}
    JsonNode call(String method,String path,String who,Object body)throws Exception {
        var builder=switch(method){case "POST"->post(path);case "PUT"->put(path);default->get(path);};
        builder.header("Authorization",token(who));if(body!=null)builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response=mvc.perform(builder).andReturn().getResponse();assertEquals(200,response.getStatus(),"Request HTTP status");return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    JsonNode ok(String method,String path,String who,Object body)throws Exception{var r=call(method,path,who,body);assertEquals(0,r.path("code").asInt(),r.toString());return r.path("data");}
    MembershipProductEntity product(Long venue,String type,boolean cross){var p=new MembershipProductEntity();p.setName("边界"+UUID.randomUUID());p.setCategory("PACKAGE");p.setProductType(type);p.setValidityDays(90);p.setTotalUses(3);p.setPrice(new BigDecimal("30"));p.setAccessAllowed(true);p.setStatus("ACTIVE");p.setVenueId(venue);products.save(p);if(cross){var s=new ProductVenueEntity();s.setProductId(p.getId());s.setVenueId(venue.equals(a)?b:a);scopes.save(s);}return p;}
    long sell(MembershipProductEntity p)throws Exception{ok("POST","/api/v1/commercial/orders","admin",Map.of("memberId",memberId,"productId",p.getId(),"paymentMethod","CASH","idempotencyKey",UUID.randomUUID().toString()));return entitlements.findByMemberIdOrderByIdDesc(memberId).get(0).getId();}
    CourseScheduleEntity schedule(Long venue,String type,Long coach,int capacity){var c=new CourseEntity();c.setName("边界课程");c.setType(type);c.setVenueId(venue);c.setStatus("ACTIVE");c.setMaxCapacity(capacity);c.setFee(BigDecimal.ZERO);courses.save(c);var s=new CourseScheduleEntity();s.setCourseId(c.getId());s.setCoachId(coach);s.setCoachName("教练");s.setVenueId(venue);s.setScheduledStartTime(LocalDateTime.now().plusDays(2));s.setScheduledEndTime(LocalDateTime.now().plusDays(2).plusHours(1));s.setMaxStudents(capacity);s.setEnrolledCount(0);s.setStatus("SCHEDULED");return schedules.save(s);}
    BookingEntity booking(CourseScheduleEntity s,Long member,String state){var v=new BookingEntity();v.setScheduleId(s.getId());v.setMemberId(member);v.setStatus(state);v.setBookedAt(LocalDateTime.now());v.setWaitlistPosition(1);return bookings.save(v);}
    Map<String,Object> renewal(String key){return Map.of("additionalUses",2,"amount",20,"paymentMethod","CASH","idempotencyKey",key);}
    @Test void crossStoreSaleAndRenewalFundingBelongToIssuerAndRefundRemainsPossible()throws Exception{
        long id=sell(product(b,"TIMES_CARD",true));var e=entitlements.findById(id).orElseThrow();assertEquals(b,orders.findById(e.getSourceOrderId()).orElseThrow().getVenueId());
        ok("POST","/api/v1/commercial/entitlements/"+id+"/renew","admin",renewal(UUID.randomUUID().toString()));
        assertTrue(orders.findAll().stream().filter(o->o.getMemberId().equals(memberId)&&o.getProductId().equals(e.getProductId())).allMatch(o->o.getVenueId().equals(b)));
        var q=ok("GET","/api/v1/finance/entitlements/"+id+"/quote","admin",null);
        ok("POST","/api/v1/finance/refunds","admin",Map.of("entitlementId",id,"version",q.path("version").asLong(),"remainingUses",5,"reason","资金归属回归","idempotencyKey",UUID.randomUUID().toString()));
    }
    @Test void coachBookingListContainsOnlyOwnScheduleAndMinimalMemberFields()throws Exception{
        var own=booking(schedule(a,"GROUP",coachId,10),memberId,"BOOKED");var foreign=booking(schedule(a,"GROUP",users.findByUsername(coachB).orElseThrow().getId(),10),memberId,"BOOKED");
        var rows=ok("GET","/api/v1/admin/bookings","coach",null);assertEquals(1,rows.size());assertEquals(own.getId().longValue(),rows.get(0).path("id").asLong());
        assertFalse(rows.get(0).path("member").has("phone"));assertFalse(rows.get(0).path("member").has("accessToken"));assertFalse(rows.get(0).path("member").has("userId"));assertTrue(rows.get(0).path("member").has("realName"));assertTrue(rows.get(0).path("schedule").path("course").has("name"));
        assertEquals(2,ok("GET","/api/v1/admin/bookings","manager",null).size());
    }
    @Test void scanUsesOnlyEntitlementWhoseSnapshotCoversServingVenue()throws Exception{
        long local=sell(product(a,"TIMES_CARD",false)),covered=sell(product(a,"TIMES_CARD",true));var e=entitlements.findById(local).orElseThrow();e.setValidUntil(LocalDate.now().plusDays(3));entitlements.save(e);
        var reception=users.findByUsername("reception").orElseThrow();reception.setVenueId(b);users.save(reception);
        String code=ok("GET","/api/v1/commercial/me/access-code","member",null).path("token").asText();var scan=ok("POST","/api/v1/commercial/access/scan","reception",Map.of("token",code));
        assertEquals("APPROVED",scan.path("result").asText());assertEquals(covered,scan.path("entitlementId").asLong());assertEquals(3,entitlements.findById(local).orElseThrow().getRemainingUses());assertEquals(2,entitlements.findById(covered).orElseThrow().getRemainingUses());
        reception.setVenueId(a);users.save(reception);
    }
    @Test void renewalReplayDoesNotDuplicateMoneyAndDifferentPayloadConflicts()throws Exception{
        long id=sell(product(a,"TIMES_CARD",false));String key=UUID.randomUUID().toString();long before=orders.count(),beforePayments=payments.count();
        ok("POST","/api/v1/commercial/entitlements/"+id+"/renew","admin",renewal(key));ok("POST","/api/v1/commercial/entitlements/"+id+"/renew","admin",renewal(key));
        assertEquals(before+1,orders.count());assertEquals(beforePayments+1,payments.count());assertEquals(5,entitlements.findById(id).orElseThrow().getRemainingUses());assertEquals(5000,accounts.findByEntitlementId(id).orElseThrow().getBalanceCents());
        var changed=new HashMap<>(renewal(key));changed.put("amount",30);assertNotEquals(0,call("POST","/api/v1/commercial/entitlements/"+id+"/renew","admin",changed).path("code").asInt());
    }
    @Test void concurrentRenewalWithOneKeyCreatesOneFundingSource()throws Exception{
        long id=sell(product(a,"TIMES_CARD",false));long before=orders.count();String key=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var pending=new ArrayList<Future<Integer>>();for(int i=0;i<2;i++)pending.add(pool.submit(()->{start.await();return call("POST","/api/v1/commercial/entitlements/"+id+"/renew","admin",renewal(key)).path("code").asInt();}));start.countDown();for(var f:pending)assertEquals(0,f.get(20,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
        assertEquals(before+1,orders.count());assertEquals(5,entitlements.findById(id).orElseThrow().getRemainingUses());
    }
    @Test void changingCouponOnReplayedSaleMustConflict()throws Exception{
        var p=product(a,"TIMES_CARD",false);String key=UUID.randomUUID().toString();var body=new HashMap<String,Object>(Map.of("memberId",memberId,"productId",p.getId(),"paymentMethod","CASH","idempotencyKey",key));ok("POST","/api/v1/commercial/orders","admin",body);long unused=coupon();body.put("memberCouponId",unused);assertNotEquals(0,call("POST","/api/v1/commercial/orders","admin",body).path("code").asInt());assertEquals("UNUSED",coupons.findById(unused).orElseThrow().getStatus());
    }
    @Test void clearBlockersCommitsSuccessfulCancellationBeforeLaterFailure()throws Exception{
        long id=sell(product(a,"PRIVATE_PACKAGE",false));var bad=schedule(a,"PRIVATE",coachId,3);var good=schedule(a,"PRIVATE",coachId,3);
        long failed=ok("POST","/api/v1/admin/bookings","admin",Map.of("memberId",memberId,"scheduleId",bad.getId(),"entitlementId",id)).path("id").asLong();
        var l=lessons.findByBookingId(failed).orElseThrow();l.setStatus("PENDING");lessons.save(l);
        long passed=ok("POST","/api/v1/admin/bookings","admin",Map.of("memberId",memberId,"scheduleId",good.getId(),"entitlementId",id)).path("id").asLong();
        var result=ok("POST","/api/v1/finance/me/refunds/clear-blockers?entitlementId="+id,"member",Map.of());
        assertEquals(1,result.path("cancelled").size());assertEquals(2,result.path("skipped").size());assertEquals("CANCELLED",bookings.findById(passed).orElseThrow().getStatus());assertEquals("BOOKED",bookings.findById(failed).orElseThrow().getStatus());assertEquals(1,entitlements.findById(id).orElseThrow().getReservedUses());
    }
    @Test void ordinaryMemberCanBuyCrossStoreProductAndCancelOwnCrossStoreBooking()throws Exception{
        var p=product(b,"TIMES_CARD",true);ok("POST","/api/v1/member/purchase","member",Map.of("productId",p.getId(),"idempotencyKey",UUID.randomUUID().toString()));
        var s=schedule(b,"GROUP",users.findByUsername(coachB).orElseThrow().getId(),10);ok("POST","/api/v1/member/bookings","member",Map.of("scheduleId",s.getId()));long bid=bookings.findByScheduleIdAndMemberId(s.getId(),memberId).orElseThrow().getId();ok("PUT","/api/v1/member/bookings/"+bid+"/cancel","member",null);assertEquals("CANCELLED",bookings.findById(bid).orElseThrow().getStatus());
    }
    @Test void crossStoreWaitlistPromotesAndCanBeCancelledByOwner()throws Exception{
        sell(product(a,"TIMES_CARD",true));var s=schedule(b,"GROUP",users.findByUsername(coachB).orElseThrow().getId(),1);s.setEnrolledCount(1);schedules.save(s);var other=members.findAll().stream().filter(m->!m.getId().equals(memberId)).findFirst().orElseThrow();var full=booking(s,other.getId(),"BOOKED");
        ok("POST","/api/v1/member/schedules/"+s.getId()+"/waitlist","member",Map.of());long bid=bookings.findByScheduleIdAndMemberId(s.getId(),memberId).orElseThrow().getId();ok("PUT","/api/v1/admin/bookings/"+full.getId()+"/cancel","admin",null);assertEquals("BOOKED",bookings.findById(bid).orElseThrow().getStatus());ok("PUT","/api/v1/member/bookings/"+bid+"/cancel","member",null);
    }
    @Test void crossStorePrivatePackageReservesAndOwnerConfirmsOnce()throws Exception{
        long id=sell(product(a,"PRIVATE_PACKAGE",true));var s=schedule(b,"PRIVATE",users.findByUsername(coachB).orElseThrow().getId(),1);ok("POST","/api/v1/member/bookings","member",Map.of("scheduleId",s.getId(),"entitlementId",id));long bid=bookings.findByScheduleIdAndMemberId(s.getId(),memberId).orElseThrow().getId();s.setScheduledStartTime(LocalDateTime.now().minusHours(2));s.setScheduledEndTime(LocalDateTime.now().minusHours(1));schedules.save(s);ok("POST","/api/v1/admin/bookings/"+bid+"/check-in",coachB,Map.of());ok("POST","/api/v1/training/lessons/"+bid+"/submit",coachB,Map.of());ok("POST","/api/v1/training/lessons/"+bid+"/confirm","member",Map.of());ok("POST","/api/v1/training/lessons/"+bid+"/confirm","member",Map.of());assertEquals(2,entitlements.findById(id).orElseThrow().getRemainingUses());
    }
    @Test void ordinaryRolesCannotManageOutsideVenueAndUncoveredMemberCannotConsume()throws Exception{
        var p=product(b,"TIMES_CARD",false);assertNotEquals(0,call("POST","/api/v1/member/purchase","member",Map.of("productId",p.getId(),"idempotencyKey",UUID.randomUUID().toString())).path("code").asInt());
        var s=schedule(b,"GROUP",users.findByUsername(coachB).orElseThrow().getId(),10);assertNotEquals(0,call("POST","/api/v1/member/bookings","member",Map.of("scheduleId",s.getId())).path("code").asInt());assertNotEquals(0,call("POST","/api/v1/member/schedules/"+s.getId()+"/waitlist","member",Map.of()).path("code").asInt());
        assertNotEquals(0,call("POST","/api/v1/admin/bookings","reception",Map.of("memberId",memberId,"scheduleId",s.getId())).path("code").asInt());assertNotEquals(0,call("POST","/api/v1/commercial/orders","reception",Map.of("memberId",memberId,"productId",p.getId(),"paymentMethod","CASH","idempotencyKey",UUID.randomUUID().toString())).path("code").asInt());
        var coveredProduct=product(b,"TIMES_CARD",true);sell(coveredProduct);
        assertNotEquals(0,call("POST","/api/v1/admin/bookings","reception",Map.of("memberId",memberId,"scheduleId",s.getId())).path("code").asInt());
        assertNotEquals(0,call("POST","/api/v1/commercial/orders","reception",Map.of("memberId",memberId,"productId",coveredProduct.getId(),"paymentMethod","CASH","idempotencyKey",UUID.randomUUID().toString())).path("code").asInt());
        var other=members.findAll().stream().filter(m->!m.getId().equals(memberId)).findFirst().orElseThrow();var bid=booking(s,other.getId(),"BOOKED").getId();assertNotEquals(0,call("PUT","/api/v1/member/bookings/"+bid+"/cancel","member",null).path("code").asInt());
    }
    @Test void expiredFutureAndExhaustedSnapshotsCannotAuthorizeCrossStoreBookings()throws Exception{
        long id=sell(product(a,"TIMES_CARD",true));var s=schedule(b,"GROUP",users.findByUsername(coachB).orElseThrow().getId(),10);
        for(String mode:List.of("EXPIRED_DATE","FUTURE_DATE","EMPTY")){
            var e=entitlements.findById(id).orElseThrow();e.setStatus("ACTIVE");e.setValidFrom(LocalDate.now().minusDays(1));e.setValidUntil(LocalDate.now().plusDays(90));e.setRemainingUses(3);
            if(mode.equals("EXPIRED_DATE"))e.setValidUntil(LocalDate.now().minusDays(1));if(mode.equals("FUTURE_DATE"))e.setValidFrom(LocalDate.now().plusDays(5));if(mode.equals("EMPTY"))e.setRemainingUses(0);entitlements.save(e);
            assertNotEquals(0,call("POST","/api/v1/member/bookings","member",Map.of("scheduleId",s.getId())).path("code").asInt(),mode);
            assertNotEquals(0,call("POST","/api/v1/member/schedules/"+s.getId()+"/waitlist","member",Map.of()).path("code").asInt(),mode);
            assertTrue(ok("GET","/api/v1/member/schedules","member",null).findValues("venueId").stream().noneMatch(v->v.asLong()==b),mode);
        }
    }
    long coupon(){
        var t=new com.gym.fitness.module.coupon.entity.CouponTemplateEntity();t.setVenueId(a);t.setName("幂等优惠券");t.setCouponType("FULL_REDUCTION");t.setDiscountCents(100L);t.setValidFrom(LocalDate.now().minusDays(1));t.setValidUntil(LocalDate.now().plusDays(10));t.setStatus("ACTIVE");couponTemplates.save(t);
        var c=new com.gym.fitness.module.coupon.entity.MemberCouponEntity();c.setCouponTemplateId(t.getId());c.setMemberId(memberId);c.setVenueId(a);c.setCode(UUID.randomUUID().toString());c.setStatus("UNUSED");c.setIssuedAt(LocalDateTime.now());c.setValidFrom(t.getValidFrom());c.setValidUntil(t.getValidUntil());return coupons.save(c).getId();
    }
    @Test void replayCannotSwitchFromCouponAToCouponB()throws Exception{
        var p=product(a,"TIMES_CARD",false);long first=coupon(),second=coupon();var body=new HashMap<String,Object>(Map.of("memberId",memberId,"productId",p.getId(),"paymentMethod","CASH","idempotencyKey",UUID.randomUUID().toString(),"memberCouponId",first));ok("POST","/api/v1/commercial/orders","admin",body);
        long before=orders.count();body.put("memberCouponId",second);assertNotEquals(0,call("POST","/api/v1/commercial/orders","admin",body).path("code").asInt());assertEquals(before,orders.count());assertEquals("UNUSED",coupons.findById(second).orElseThrow().getStatus());
    }

    @Test void memberCanQuoteRequestReplayAndCancelOwnCrossStoreRefundButCannotTouchAnotherMember()throws Exception{
        long id=sell(product(b,"TIMES_CARD",true));var q=ok("GET","/api/v1/finance/me/entitlements/"+id+"/quote","member",null);
        var body=Map.of("entitlementId",id,"version",q.path("version").asLong(),"remainingUses",3,"reason","跨店本人退款","idempotencyKey",UUID.randomUUID().toString());
        long rid=ok("POST","/api/v1/finance/me/refunds","member",body).path("id").asLong();assertEquals(rid,ok("POST","/api/v1/finance/me/refunds","member",body).path("id").asLong());
        assertNotEquals(0,call("GET","/api/v1/finance/entitlements/"+id+"/quote","reception",null).path("code").asInt());
        var other=members.findAll().stream().filter(m->!m.getId().equals(memberId)).findFirst().orElseThrow();var e=entitlements.findById(id).orElseThrow();e.setMemberId(other.getId());entitlements.save(e);
        assertNotEquals(0,call("GET","/api/v1/finance/me/entitlements/"+id+"/quote","member",null).path("code").asInt());assertNotEquals(0,call("POST","/api/v1/finance/me/refunds","member",body).path("code").asInt());assertNotEquals(0,call("POST","/api/v1/finance/me/refunds/"+rid+"/cancel","member",Map.of("reason","越权撤回")).path("code").asInt());
        e.setMemberId(memberId);entitlements.save(e);ok("POST","/api/v1/finance/me/refunds/"+rid+"/cancel","member",Map.of("reason","本人撤回"));assertNull(accounts.findByEntitlementId(id).orElseThrow().getActiveRefundId());
    }

    @Test void localVenueCouponCannotPayForCrossStoreProduct()throws Exception{
        var p=product(b,"TIMES_CARD",true);long cid=coupon(),before=orders.count();
        assertNotEquals(0,call("POST","/api/v1/member/purchase","member",Map.of("productId",p.getId(),"memberCouponId",cid,"idempotencyKey",UUID.randomUUID().toString())).path("code").asInt());
        assertEquals(before,orders.count());assertEquals("UNUSED",coupons.findById(cid).orElseThrow().getStatus());
        ok("POST","/api/v1/member/purchase","member",Map.of("productId",p.getId(),"idempotencyKey",UUID.randomUUID().toString()));
    }
    @Test void crossStoreCouponQuoteAgreesWithPurchaseVenueScope()throws Exception{
        var p=product(b,"TIMES_CARD",true);coupon();var q=ok("GET","/api/v1/commercial/me/coupon-quote?productId="+p.getId(),"member",null);assertEquals(0,q.path("availableCount").asInt());assertEquals(0,q.path("options").size());
        var uncovered=product(b,"TIMES_CARD",false);assertNotEquals(0,call("GET","/api/v1/commercial/me/coupon-quote?productId="+uncovered.getId(),"member",null).path("code").asInt());
    }
    @Test void separatedPaymentAndNoteValuesCannotCollideInRenewalFingerprint()throws Exception{
        long id=sell(product(a,"TIMES_CARD",false));var body=new HashMap<>(renewal(UUID.randomUUID().toString()));body.put("paymentMethod","CASH|X");body.put("note","Y");ok("POST","/api/v1/commercial/entitlements/"+id+"/renew","admin",body);body.put("paymentMethod","CASH");body.put("note","X|Y");assertNotEquals(0,call("POST","/api/v1/commercial/entitlements/"+id+"/renew","admin",body).path("code").asInt());
    }
    @Test void separatedPaymentAndRemarkValuesCannotCollideInSaleFingerprint()throws Exception{
        var p=product(a,"TIMES_CARD",false);var body=new HashMap<String,Object>(Map.of("memberId",memberId,"productId",p.getId(),"paymentMethod","CASH|X","remark","Y","idempotencyKey",UUID.randomUUID().toString()));ok("POST","/api/v1/commercial/orders","admin",body);body.put("paymentMethod","CASH");body.put("remark","X|Y");assertNotEquals(0,call("POST","/api/v1/commercial/orders","admin",body).path("code").asInt());
    }
    @Test void numericallyEqualRenewalAmountsReplayTheSameIntent()throws Exception{
        long id=sell(product(a,"TIMES_CARD",false));var body=new HashMap<>(renewal(UUID.randomUUID().toString()));body.put("amount",new BigDecimal("20.00"));ok("POST","/api/v1/commercial/entitlements/"+id+"/renew","admin",body);body.put("amount",new BigDecimal("20.0"));ok("POST","/api/v1/commercial/entitlements/"+id+"/renew","admin",body);assertEquals(5,entitlements.findById(id).orElseThrow().getRemainingUses());
    }
    @Test void crossStoreRefundAndBookingCannotBothCommitAfterReadingOldState()throws Exception{
        assertRefundAndNewBookingSerialized(false);
    }
    @Test void crossStoreRefundAndWaitlistCannotBothCommitAfterReadingOldState()throws Exception{
        assertRefundAndNewBookingSerialized(true);
    }
    void assertRefundAndNewBookingSerialized(boolean waitlist)throws Exception{assertRefundAndNewBookingSerialized(waitlist,"member");}
    void assertRefundAndNewBookingSerialized(boolean waitlist,String requester)throws Exception{
        long id=sell(product(b,"TIMES_CARD",true));var s=schedule(b,"GROUP",users.findByUsername(coachB).orElseThrow().getId(),1);if(waitlist){s.setEnrolledCount(1);schedules.save(s);}
        var q=ok("GET","/api/v1/finance/me/entitlements/"+id+"/quote","member",null);var checked=new CountDownLatch(1);var resume=new CountDownLatch(1);
        // Keep the real transactional refund paused after its service-blocker read, before activeRefundId is written.
        org.mockito.Mockito.doAnswer(invocation->{checked.countDown();if(!resume.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Concurrency test timed out");return invocation.callRealMethod();}).when(values).pause(org.mockito.ArgumentMatchers.any());
        var pool=Executors.newFixedThreadPool(2);
        try{
            var refund=pool.submit(()->call("POST",requester.equals("member")?"/api/v1/finance/me/refunds":"/api/v1/finance/refunds",requester,Map.of("entitlementId",id,"version",q.path("version").asLong(),"remainingUses",3,"reason","并发退款","idempotencyKey",UUID.randomUUID().toString())).path("code").asInt());
            assertTrue(checked.await(5,TimeUnit.SECONDS));
            var addition=pool.submit(()->call("POST",waitlist?"/api/v1/member/schedules/"+s.getId()+"/waitlist":"/api/v1/member/bookings","member",Map.of("scheduleId",s.getId())).path("code").asInt());
            Integer additionCode=null;try{additionCode=addition.get(2,TimeUnit.SECONDS);}catch(TimeoutException stillWaiting){}finally{resume.countDown();}
            int refundCode=refund.get(10,TimeUnit.SECONDS);if(additionCode==null)additionCode=addition.get(10,TimeUnit.SECONDS);
            assertEquals(0,refundCode,"Refund that passed blockers first must commit");
            assertNotEquals(0,additionCode,"New service must be rejected after the refund commits");
            boolean service=bookings.findByScheduleIdAndMemberId(s.getId(),memberId).filter(v->Set.of("BOOKED","WAITLISTED").contains(v.getStatus())).isPresent();
            assertFalse(service&&accounts.findByEntitlementId(id).orElseThrow().getActiveRefundId()!=null,"No unfinished service may coexist with an active refund");
        }finally{resume.countDown();pool.shutdownNow();org.mockito.Mockito.reset(values);}
    }

    @Test void employeeCrossStoreRefundUsesSameGuardAsMemberBooking()throws Exception{
        assertRefundAndNewBookingSerialized(false,"admin");
    }
    @Test void bookingAlreadyCheckedAndUncommittedMustBlockCrossStoreRefund()throws Exception{
        long id=sell(product(b,"TIMES_CARD",true));var s=schedule(b,"GROUP",users.findByUsername(coachB).orElseThrow().getId(),2);
        var q=ok("GET","/api/v1/finance/me/entitlements/"+id+"/quote","member",null);var checked=new CountDownLatch(1);var resume=new CountDownLatch(1);
        // Execute real package/refund validation, then hold the actual booking transaction before commit.
        org.mockito.Mockito.doAnswer(invocation->{Object result=invocation.callRealMethod();checked.countDown();if(!resume.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Concurrency test timed out");return result;}).when(training).reserve(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        var pool=Executors.newFixedThreadPool(2);
        try{
            var addition=pool.submit(()->call("POST","/api/v1/member/bookings","member",Map.of("scheduleId",s.getId())).path("code").asInt());assertTrue(checked.await(5,TimeUnit.SECONDS));
            var refund=pool.submit(()->call("POST","/api/v1/finance/me/refunds","member",Map.of("entitlementId",id,"version",q.path("version").asLong(),"remainingUses",3,"reason","并发退款","idempotencyKey",UUID.randomUUID().toString())).path("code").asInt());
            Integer refundCode=null;try{refundCode=refund.get(2,TimeUnit.SECONDS);}catch(TimeoutException stillWaiting){}finally{resume.countDown();}
            int additionCode=addition.get(10,TimeUnit.SECONDS);if(refundCode==null)refundCode=refund.get(10,TimeUnit.SECONDS);assertFalse(refundCode==0&&additionCode==0,"Uncommitted booking and refund cannot both commit");assertEquals(0,additionCode);assertNotEquals(0,refundCode);
        }finally{resume.countDown();pool.shutdownNow();org.mockito.Mockito.reset(training);}
    }

}
