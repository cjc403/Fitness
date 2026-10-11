package com.gym.fitness;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.organization.repository.*;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=true","app.ai.enabled=false","fitness.operations.enabled=false"})
@AutoConfigureMockMvc
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="training.test.pgurl",matches=".+")
class TrainingPlanPostgresConcurrencyTest {
    @org.springframework.test.context.DynamicPropertySource
    static void isolatedPostgres(org.springframework.test.context.DynamicPropertyRegistry registry) {
        String url=System.getProperty("training.test.pgurl");
        if(!"jdbc:postgresql://127.0.0.1:55450/training_junit".equals(url))throw new IllegalArgumentException("Only exact isolated training_junit JDBC is allowed");
        registry.add("spring.datasource.url",()->url);registry.add("spring.datasource.username",()->"fitness_user");registry.add("spring.datasource.password",()->"isolated-test");registry.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired SysUserRepository users; @Autowired RoleRepository roles; @Autowired VenueRepository venues;
    @Autowired MemberRepository members; @Autowired JwtTokenProvider jwt;
    @Autowired com.gym.fitness.module.course.repository.CourseRepository courses;
    @Autowired com.gym.fitness.module.course.repository.CourseScheduleRepository schedules;
    @Autowired com.gym.fitness.module.booking.repository.BookingRepository bookings;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired javax.sql.DataSource datasource;

    private <T> T tx(java.util.function.Supplier<T> action){return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s->action.get());}
    private record Fixture(SysUserEntity coach,MemberEntity member,long plan,long exercise){}
    private Fixture fixture(boolean publish)throws Exception {var c=tx(()->account("COACH",0));var m=tx(()->member(c));var d=write(c,"",create(m),0);long id=d.path("plan").path("id").asLong();if(publish)d=write(c,"/"+id+"/publish",version(0),0);return new Fixture(c,m,id,d.path("exercises").get(0).path("id").asLong());}
    private JsonNode raw(SysUserEntity u,String path,Object body)throws Exception {return json.readTree(mvc.perform(post(path).header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private List<JsonNode> race(java.util.concurrent.Callable<JsonNode> one,java.util.concurrent.Callable<JsonNode> two)throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try{var a=pool.submit(()->{gate.await();return one.call();});var b=pool.submit(()->{gate.await();return two.call();});gate.countDown();return List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS));}
        finally{pool.shutdownNow();pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);}
    }
    private void codes(List<JsonNode> responses,int... codes){assertThat(responses.stream().map(n->n.path("code").asInt()).toList()).containsExactlyInAnyOrder(Arrays.stream(codes).boxed().toArray(Integer[]::new));}
    private String path(long id,String suffix){return "/api/v1/training-plans/"+id+suffix;}
    @Test void concurrentPublicationAcrossTwoCoachesHasOneActivePlan()throws Exception {
        var f=fixture(false);var other=tx(()->account("COACH",0));tx(()->booking(other,f.member(),"BOOKED",LocalDate.now()));
        long second=write(other,"",create(f.member()),0).path("plan").path("id").asLong();
        codes(race(()->raw(f.coach(),path(f.plan(),"/publish"),version(0)),()->raw(other,path(second,"/publish"),version(0))),0,2600);
        assertThat(jdbc.queryForObject("select count(*) from training_plan where member_id=? and status='ACTIVE'",Long.class,f.member().getId())).isEqualTo(1);
    }
    @Test void concurrentRecordingOfOneBookingCommitsOnlyOneEffectiveSession()throws Exception {
        var f=fixture(true);var b=tx(()->booking(f.coach(),f.member(),"ATTENDED",LocalDate.now()));
        var a=session(1,List.of(Map.of("exerciseId",f.exercise(),"status","DONE","sets",2,"reps",9)));a.put("bookingId",b.getId());var bdy=new LinkedHashMap<>(a);bdy.put("idempotencyKey",key());
        codes(race(()->raw(f.coach(),path(f.plan(),"/sessions"),a),()->raw(f.coach(),path(f.plan(),"/sessions"),bdy)),0,2600);
        assertThat(jdbc.queryForObject("select count(*) from training_session where active_booking_key=?",Long.class,b.getId())).isEqualTo(1);
    }
    @Test void simultaneousSameKeyCreatesExactlyOneEventAndReturnsCurrentObject()throws Exception {
        var c=tx(()->account("COACH",0));var m=tx(()->member(c));var r=create(m);
        var results=race(()->raw(c,"/api/v1/training-plans",r),()->raw(c,"/api/v1/training-plans",r));codes(results,0,0);
        assertThat(results.get(0).path("data").path("plan").path("id")).isEqualTo(results.get(1).path("data").path("plan").path("id"));
        assertThat(jdbc.queryForObject("select count(*) from training_plan_event where member_id=?",Long.class,m.getId())).isEqualTo(1);
    }
    @Test void concurrentFeedbackAndVoidSerializeOnSessionVersion()throws Exception {
        var f=fixture(true);var owner=tx(()->account("MEMBER",0));tx(()->{var m=members.findById(f.member().getId()).orElseThrow();m.setUserId(owner.getId());members.saveAndFlush(m);return m;});
        var d=write(f.coach(),"/"+f.plan()+"/sessions",session(1,List.of(Map.of("exerciseId",f.exercise(),"status","DONE","sets",2,"reps",9))),0);long sid=d.path("sessions").path("records").get(0).path("id").asLong();var feedback=version(0);feedback.put("rating",5);
        codes(race(()->raw(f.coach(),path(f.plan(),"/sessions/"+sid+"/void"),reason(0,"错误")),()->raw(owner,"/api/v1/member/training-plans/"+f.plan()+"/sessions/"+sid+"/feedback",feedback)),0,2600);
        assertThat(jdbc.queryForObject("select version from training_session where id=?",Long.class,sid)).isEqualTo(1);
    }
    @Test void waitingWriterRefreshesMemberRelationBeforeCheckingIdempotency()throws Exception {
        var f=fixture(false);var request=version(0);var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var gate=datasource.getConnection()) {
            gate.setAutoCommit(false);try(var stmt=gate.prepareStatement("select id from member where id=? for no key update")){stmt.setLong(1,f.member().getId());stmt.executeQuery().close();}
            var future=pool.submit(()->raw(f.coach(),path(f.plan(),"/publish"),request));
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);boolean waiting=false;
            while(System.nanoTime()<deadline){if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%member%'",Long.class)>0){waiting=true;break;}Thread.sleep(20);}
            assertThat(waiting).as("actual waiting member row lock").isTrue();
            try(var stmt=gate.prepareStatement("update member set blacklist_flag=true where id=?")){stmt.setLong(1,f.member().getId());stmt.executeUpdate();}gate.commit();
            assertThat(future.get(20,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()).isEqualTo(1003);
            assertThat(jdbc.queryForObject("select status from training_plan where id=?",String.class,f.plan())).isEqualTo("DRAFT");
        }finally{pool.shutdownNow();pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);}
    }
    @Test void databaseUniqueKeysRejectBypassingServiceGuards()throws Exception {
        var f=fixture(true);long second=write(f.coach(),"",create(f.member()),0).path("plan").path("id").asLong();
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update("update training_plan set status='ACTIVE',active_member_key=? where id=?",f.coach().getVenueId()+"/"+f.member().getId(),second)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select status from training_plan where id=?",String.class,second)).isEqualTo("DRAFT");
    }
    @Test void waitingFeedbackRechecksInactiveAndBlacklistBeforeFreshWriteAndSuccessfulReplay()throws Exception {
        for(boolean blacklist:List.of(false,true))for(boolean replay:List.of(false,true))waitingFeedbackState(blacklist,replay);
    }
    private void waitingFeedbackState(boolean blacklist,boolean replay)throws Exception {
        var f=fixture(true);var owner=tx(()->account("MEMBER",0));tx(()->{var m=members.findById(f.member().getId()).orElseThrow();m.setUserId(owner.getId());members.saveAndFlush(m);return m;});
        var d=write(f.coach(),"/"+f.plan()+"/sessions",session(1,List.of(Map.of("exerciseId",f.exercise(),"status","DONE","sets",2,"reps",9))),0);long sid=d.path("sessions").path("records").get(0).path("id").asLong();
        var successful=version(0);successful.put("rating",4);successful.put("content","原反馈");memberWrite(owner,"/"+f.plan()+"/sessions/"+sid+"/feedback",successful,0);
        long eventCount=jdbc.queryForObject("select count(*) from training_plan_event where plan_id=?",Long.class,f.plan());var request=replay?successful:version(1);if(!replay)request.put("rating",5);
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var gate=datasource.getConnection()) {
            gate.setAutoCommit(false);try(var stmt=gate.prepareStatement("select id from member where id=? for no key update")){stmt.setLong(1,f.member().getId());stmt.executeQuery().close();}
            var future=pool.submit(()->raw(owner,"/api/v1/member/training-plans/"+f.plan()+"/sessions/"+sid+"/feedback",request));
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);boolean waiting=false;
            while(System.nanoTime()<deadline){if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%member%'",Long.class)>0){waiting=true;break;}Thread.sleep(20);}
            assertThat(waiting).as("feedback has reached actual member row lock before profile state changes").isTrue();
            String sql=blacklist?"update member set blacklist_flag=true where id=?":"update member set status='INACTIVE' where id=?";
            try(var stmt=gate.prepareStatement(sql)){stmt.setLong(1,f.member().getId());assertThat(stmt.executeUpdate()).isEqualTo(1);}gate.commit();
            assertThat(future.get(20,java.util.concurrent.TimeUnit.SECONDS).path("code").asInt()).isEqualTo(1003);
            assertThat(jdbc.queryForObject("select version from training_session where id=?",Long.class,sid)).isEqualTo(1);assertThat(jdbc.queryForObject("select feedback_rating from training_session where id=?",Integer.class,sid)).isEqualTo(4);assertThat(jdbc.queryForObject("select feedback_content from training_session where id=?",String.class,sid)).isEqualTo("原反馈");assertThat(jdbc.queryForObject("select count(*) from training_plan_event where plan_id=?",Long.class,f.plan())).isEqualTo(eventCount);
        }finally{pool.shutdownNow();pool.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);}
    }
    protected com.gym.fitness.module.booking.entity.BookingEntity booking(SysUserEntity c,MemberEntity m,String status,LocalDate day) {
        var course=new com.gym.fitness.module.course.entity.CourseEntity();course.setName("隔离课程");course.setType("PRIVATE");course.setVenueId(c.getVenueId());courses.saveAndFlush(course);
        var schedule=new com.gym.fitness.module.course.entity.CourseScheduleEntity();schedule.setCourseId(course.getId());schedule.setCourse(course);schedule.setCoachId(c.getId());schedule.setVenueId(c.getVenueId());schedule.setScheduledStartTime(day.atTime(12,0));schedule.setScheduledEndTime(day.atTime(13,0));schedules.saveAndFlush(schedule);
        var b=new com.gym.fitness.module.booking.entity.BookingEntity();b.setMemberId(m.getId());b.setScheduleId(schedule.getId());b.setSchedule(schedule);b.setStatus(status);b.setBookedAt(LocalDateTime.now());return bookings.saveAndFlush(b);
    }
    protected Map<String,Object> version(long v){var r=new LinkedHashMap<String,Object>();r.put("expectedVersion",v);r.put("idempotencyKey",key());return r;}
    protected Map<String,Object> reason(long v,String reason){var r=version(v);r.put("reason",reason);return r;}
    protected Map<String,Object> session(long v,List<?> exercises){var r=version(v);r.put("performedOn",LocalDate.now());r.put("durationMinutes",40);r.put("exercises",exercises);return r;}
    protected JsonNode memberRead(SysUserEntity u,String suffix,int code)throws Exception {return json.readTree(mvc.perform(get("/api/v1/member/training-plans"+suffix).header("Authorization",token(u))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
    protected JsonNode memberWrite(SysUserEntity u,String suffix,Object request,int code)throws Exception {return json.readTree(mvc.perform(post("/api/v1/member/training-plans"+suffix).header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
    protected SysUserEntity account(String role,int venueIndex) {
        var u=new SysUserEntity();u.setUsername("training-"+UUID.randomUUID());u.setPasswordHash("unused-test");u.setRealName("隔离"+role);
        var r=roles.findByCode(role).orElseThrow();var v=venues.findAll().get(venueIndex);u.setRoleId(r.getId());u.setRole(r);u.setVenueId(v.getId());u.setVenue(v);u.setStatus("ACTIVE");u.setCoach("COACH".equals(role));return users.saveAndFlush(u);
    }
    protected MemberEntity member(SysUserEntity coach) {
        var m=new MemberEntity();m.setMemberNo("TP-"+UUID.randomUUID());m.setRealName("隔离学员");m.setPhone("13800000000");m.setVenueId(coach.getVenueId());m.setCoachId(coach.getId());m.setStatus("ACTIVE");m.setBlacklistFlag(false);return members.saveAndFlush(m);
    }
    protected Map<String,Object> create(MemberEntity m) {
        var r=new LinkedHashMap<String,Object>();r.put("memberId",m.getId());r.put("title","基础训练");r.put("goal","GENERAL");r.put("startDate",LocalDate.now().minusDays(2));r.put("endDate",LocalDate.now().plusDays(20));
        r.put("exercises",List.of(Map.of("name","深蹲","mode","STRENGTH","sets",3,"reps",12,"weightKg",20)));r.put("idempotencyKey",key());return r;
    }
    protected String key(){return "test-"+UUID.randomUUID();}
    protected String token(SysUserEntity u){return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole().getCode(),u.getVenueId(),u.currentSessionVersion());}
    protected JsonNode write(SysUserEntity u,String suffix,Object request,int code) throws Exception {return json.readTree(mvc.perform(post("/api/v1/training-plans"+suffix).header("Authorization",token(u)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
    protected JsonNode read(SysUserEntity u,String suffix,int code) throws Exception {return json.readTree(mvc.perform(get("/api/v1/training-plans"+suffix).header("Authorization",token(u))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
}
