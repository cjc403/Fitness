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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:training-plans;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "app.ai.enabled=false", "fitness.operations.enabled=false"})
@AutoConfigureMockMvc @Transactional
class TrainingPlanIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired SysUserRepository users; @Autowired RoleRepository roles; @Autowired VenueRepository venues;
    @Autowired MemberRepository members; @Autowired JwtTokenProvider jwt;
    @Autowired com.gym.fitness.module.course.repository.CourseRepository courses;
    @Autowired com.gym.fitness.module.course.repository.CourseScheduleRepository schedules;
    @Autowired com.gym.fitness.module.booking.repository.BookingRepository bookings;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @Test void createReplayReauthorizesOriginalVenueAfterCoachTransfer() throws Exception {
        var c=account("COACH",0);var m=member(c);var request=create(m);var d=write(c,"",request,0);long id=d.path("plan").path("id").asLong();
        assertThat(write(c,"",request,0).path("plan").path("id").asLong()).isEqualTo(id);
        long plansBefore=jdbc.queryForObject("select count(*) from training_plan",Long.class),eventsBefore=jdbc.queryForObject("select count(*) from training_plan_event",Long.class);
        c.setVenueId(venues.findAll().get(1).getId());c.setVenue(venues.findAll().get(1));users.saveAndFlush(c);booking(c,m,"BOOKED",LocalDate.now());em.clear();
        assertThat(read(c,"/students",0).path("total").asLong()).isEqualTo(1);
        read(c,"/"+id,1003);write(c,"",request,1003);
        assertThat(jdbc.queryForObject("select count(*) from training_plan",Long.class)).isEqualTo(plansBefore);assertThat(jdbc.queryForObject("select count(*) from training_plan_event",Long.class)).isEqualTo(eventsBefore);
    }
    @Test void feedbackFreshAndSuccessfulReplayRejectInactiveMemberWithoutLosingHistoryOrSupervisorArchive() throws Exception {
        feedbackMemberState("INACTIVE",false);
    }
    @Test void feedbackFreshAndSuccessfulReplayRejectBlacklistedMemberWithoutLosingHistoryOrSupervisorArchive() throws Exception {
        feedbackMemberState("ACTIVE",true);
    }
    private void feedbackMemberState(String state,boolean blacklisted)throws Exception {
        var c=account("COACH",0);var m=member(c);var owner=account("MEMBER",0);m.setUserId(owner.getId());members.saveAndFlush(m);
        var d=write(c,"",create(m),0);long id=d.path("plan").path("id").asLong();d=write(c,"/"+id+"/publish",version(0),0);long ex=d.path("exercises").get(0).path("id").asLong();
        d=write(c,"/"+id+"/sessions",session(1,List.of(Map.of("exerciseId",ex,"status","DONE","sets",3,"reps",12))),0);long sid=d.path("sessions").path("records").get(0).path("id").asLong();
        var successful=version(0);successful.put("rating",4);successful.put("content","保留旧反馈");memberWrite(owner,"/"+id+"/sessions/"+sid+"/feedback",successful,0);
        long eventsBefore=jdbc.queryForObject("select count(*) from training_plan_event where plan_id=?",Long.class,id);
        m.setStatus(state);m.setBlacklistFlag(blacklisted);m.setCoachId(null);members.saveAndFlush(m);em.clear();
        var fresh=version(1);fresh.put("rating",5);memberWrite(owner,"/"+id+"/sessions/"+sid+"/feedback",fresh,1003);memberWrite(owner,"/"+id+"/sessions/"+sid+"/feedback",successful,1003);
        var history=memberRead(owner,"/"+id,0).path("sessions").path("records").get(0);assertThat(history.path("version").asLong()).isEqualTo(1);assertThat(history.path("feedbackRating").asInt()).isEqualTo(4);assertThat(history.path("feedbackContent").asText()).isEqualTo("保留旧反馈");
        assertThat(jdbc.queryForObject("select count(*) from training_plan_event where plan_id=?",Long.class,id)).isEqualTo(eventsBefore);
        write(account("MANAGER",1),"/"+id+"/archive",reason(2,"越店"),1003);write(account("MANAGER",0),"/"+id+"/archive",reason(2,"停用会员计划处理"),0);
        assertThat(memberRead(owner,"/"+id,0).path("plan").path("status").asText()).isEqualTo("ARCHIVED");
    }
    @Test void reassignedStudentCanRecoverOnlyBySupervisorArchivingPreviousPlan() throws Exception {
        var old=account("COACH",0);var fresh=account("COACH",0);var m=member(old);
        long id=write(old,"",create(m),0).path("plan").path("id").asLong();write(old,"/"+id+"/publish",version(0),0);
        m.setCoachId(fresh.getId());members.saveAndFlush(m);em.clear();write(old,"/"+id+"/archive",reason(1,"已转教练"),1003);write(fresh,"/"+id+"/archive",reason(1,"接管"),1003);
        long next=write(fresh,"",create(m),0).path("plan").path("id").asLong();write(fresh,"/"+next+"/publish",version(0),2600);
        var manager=account("MANAGER",0);assertThat(read(manager,"/"+id,0).path("plan").path("canArchive").asBoolean()).isTrue();
        write(account("MANAGER",1),"/"+id+"/archive",reason(1,"跨店"),1003);write(manager,"/"+id+"/archive",reason(1,"转教练归档"),0);
        write(fresh,"/"+next+"/publish",version(0),0);write(account("SUPER_ADMIN",1),"/"+next+"/archive",reason(1,"总部归档"),0);
    }
    @Test void recentValidBookingGivesCrossVenueRelationButCancelledOldAndOtherCoachDoNot() throws Exception {
        var c=account("COACH",0);var foreign=member(account("COACH",1));
        var booking=booking(c,foreign,"BOOKED",LocalDate.now().minusDays(90));
        assertThat(read(c,"/students",0).path("total").asInt()).isEqualTo(1);
        long id=write(c,"",create(foreign),0).path("plan").path("id").asLong();
        assertThat(read(account("MANAGER",0),"/"+id,0).path("plan").path("canViewBody").asBoolean()).isFalse();
        assertThat(read(c,"/"+id,0).path("plan").path("canViewBody").asBoolean()).isTrue();
        booking.setStatus("CANCELLED");bookings.saveAndFlush(booking);em.clear();
        assertThat(read(c,"/students",0).path("total").asInt()).isZero();read(c,"/"+id,1003);write(c,"/"+id+"/publish",version(0),1003);
        booking=bookings.findById(booking.getId()).orElseThrow();booking.setStatus("ATTENDED");bookings.saveAndFlush(booking);
        var schedule=schedules.findById(booking.getScheduleId()).orElseThrow();schedule.setScheduledStartTime(LocalDate.now().minusDays(91).atTime(12,0));schedules.saveAndFlush(schedule);em.clear();
        read(c,"/"+id,1003);
    }
    @Test void bookedSessionLinkIsRevalidatedUniqueAndReleasedOnlyByVoid() throws Exception {
        var c=account("COACH",0);var m=member(c);var b=booking(c,m,"BOOKED",LocalDate.now());
        long oldBookingCount=bookings.count();long id=write(c,"",create(m),0).path("plan").path("id").asLong();
        var d=write(c,"/"+id+"/publish",version(0),0);long ex=d.path("exercises").get(0).path("id").asLong();
        assertThat(read(c,"/"+id+"/booking-options?performedOn="+LocalDate.now(),0).size()).isZero();
        var s=session(1,List.of(Map.of("exerciseId",ex,"status","DONE","sets",2,"reps",8)));s.put("bookingId",b.getId());write(c,"/"+id+"/sessions",s,1001);
        b.setStatus("ATTENDED");bookings.saveAndFlush(b);em.clear();assertThat(read(c,"/"+id+"/booking-options?performedOn="+LocalDate.now(),0).size()).isEqualTo(1);
        d=write(c,"/"+id+"/sessions",s,0);long sid=d.path("sessions").path("records").get(0).path("id").asLong();
        var same=session(2,List.of(Map.of("exerciseId",ex,"status","DONE","sets",2,"reps",8)));same.put("bookingId",b.getId());write(c,"/"+id+"/sessions",same,2600);
        write(c,"/"+id+"/sessions/"+sid+"/void",reason(0,"错误登记"),0);write(c,"/"+id+"/sessions",same,0);
        assertThat(bookings.count()).isEqualTo(oldBookingCount);assertThat(bookings.findById(b.getId()).orElseThrow().getStatus()).isEqualTo("ATTENDED");
    }
    protected com.gym.fitness.module.booking.entity.BookingEntity booking(SysUserEntity c,MemberEntity m,String status,LocalDate day) {
        var course=new com.gym.fitness.module.course.entity.CourseEntity();course.setName("隔离课程");course.setType("PRIVATE");course.setVenueId(c.getVenueId());courses.saveAndFlush(course);
        var schedule=new com.gym.fitness.module.course.entity.CourseScheduleEntity();schedule.setCourseId(course.getId());schedule.setCourse(course);schedule.setCoachId(c.getId());schedule.setVenueId(c.getVenueId());schedule.setScheduledStartTime(day.atTime(12,0));schedule.setScheduledEndTime(day.atTime(13,0));schedules.saveAndFlush(schedule);
        var b=new com.gym.fitness.module.booking.entity.BookingEntity();b.setMemberId(m.getId());b.setScheduleId(schedule.getId());b.setSchedule(schedule);b.setStatus(status);b.setBookedAt(LocalDateTime.now());return bookings.saveAndFlush(b);
    }
    @Test void coachCreatesDraftAndReadsExerciseTargets() throws Exception {
        var coach=account("COACH",0);var member=member(coach);
        var data=write(coach,"",create(member),0);long id=data.path("plan").path("id").asLong();
        assertThat(data.path("plan").path("status").asText()).isEqualTo("DRAFT");
        assertThat(read(coach,"/"+id,0).path("exercises").get(0).path("name").asText()).isEqualTo("深蹲");
    }
    @Test void planLifecycleIdempotencyFeedbackAndVoidAreAuditedWithoutBusinessSideEffects() throws Exception {
        var coach=account("COACH",0);var m=member(coach);var owner=account("MEMBER",0);m.setUserId(owner.getId());members.saveAndFlush(m);
        var request=create(m);var draft=write(coach,"",request,0);long id=draft.path("plan").path("id").asLong();
        assertThat(write(coach,"",request,0).path("plan").path("id").asLong()).isEqualTo(id);
        request.put("title","改变");write(coach,"",request,2600);
        memberRead(owner,"/"+id,1003);assertThat(memberRead(owner,"?page=1&size=20",0).path("plans").path("total").asLong()).isZero();
        var publish=version(0);var active=write(coach,"/"+id+"/publish",publish,0);
        assertThat(active.path("plan").path("version").asLong()).isEqualTo(1);
        var second=write(coach,"",create(m),0);write(coach,"/"+second.path("plan").path("id").asLong()+"/publish",version(0),2600);
        var edit=create(m);edit.remove("memberId");edit.put("expectedVersion",1);write(coach,"/"+id+"/draft",edit,2600);
        long ex=active.path("exercises").get(0).path("id").asLong();var recording=session(1,List.of(Map.of("exerciseId",ex,"status","DONE","sets",2,"reps",10,"weightKg",15)));
        var recorded=write(coach,"/"+id+"/sessions",recording,0);long sid=recorded.path("sessions").path("records").get(0).path("id").asLong();
        assertThat(write(coach,"/"+id+"/sessions",recording,0).path("sessions").path("total").asInt()).isEqualTo(1);
        var feedback=version(0);feedback.put("rating",4);feedback.put("content","感觉很好");var reviewed=memberWrite(owner,"/"+id+"/sessions/"+sid+"/feedback",feedback,0);
        assertThat(reviewed.path("events").size()).isZero();assertThat(reviewed.path("eventTotal").asInt()).isZero();
        feedback=version(1);feedback.put("rating",5);memberWrite(owner,"/"+id+"/sessions/"+sid+"/feedback",feedback,0);
        write(coach,"/"+id+"/archive",reason(2,"周期结束"),0);
        var voided=write(coach,"/"+id+"/sessions/"+sid+"/void",reason(2,"登记错误"),0);
        assertThat(voided.path("plan").path("sessionCount").asInt()).isZero();assertThat(voided.path("plan").path("lastSessionOn").isNull()).isTrue();
        assertThat(voided.path("sessions").path("records").get(0).path("feedbackRating").asInt()).isEqualTo(5);
        feedback=version(3);feedback.put("rating",3);memberWrite(owner,"/"+id+"/sessions/"+sid+"/feedback",feedback,2600);
        assertThat(voided.path("eventTotal").asInt()).isEqualTo(7);
        assertThat(write(coach,"/"+id+"/publish",publish,0).path("plan").path("status").asText()).isEqualTo("ARCHIVED");
    }
    @Test void pagingScopeAndReadOnlyRolesDoNotLeakDraftsOrPrivateFields() throws Exception {
        var c=account("COACH",0);var m=member(c);long id=write(c,"",create(m),0).path("plan").path("id").asLong();
        assertThat(read(c,"/students?size=1",0).path("records").get(0).has("phone")).isFalse();
        var other=account("COACH",0);read(other,"/"+id,1003);assertThat(read(other,"/workbench",0).path("plans").path("total").asInt()).isZero();
        var manager=account("MANAGER",0);assertThat(read(manager,"/"+id,0).path("plan").path("canEdit").asBoolean()).isFalse();
        read(account("MANAGER",1),"/"+id,1003);read(c,"/workbench?venueId="+venues.findAll().get(1).getId(),1003);
        assertThat(read(account("SUPER_ADMIN",0),"/workbench",0).path("plans").path("total").asInt()).isEqualTo(1);
        for(String role:List.of("MANAGER","SUPER_ADMIN","SALES","RECEPTION"))mvc.perform(post("/api/v1/training-plans").header("Authorization",token(account(role,0))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(create(m)))).andExpect(status().isForbidden());
        read(c,"/workbench?page=0",1001);read(c,"/"+id+"?sessionSize=101",1001);
    }
    @Test void malformedTargetsDatesVersionsAndAllSkippedSessionsAreRejected() throws Exception {
        var c=account("COACH",0);var m=member(c);var r=create(m);
        r.put("exercises",List.of(Map.of("name","深蹲","mode","STRENGTH","sets",3,"reps",12,"minutes",1)));write(c,"",r,1001);
        r=create(m);r.put("startDate",LocalDate.now().minusDays(366));write(c,"",r,1001);
        r=create(m);r.put("exercises",List.of(Map.of("name","有氧","mode","DURATION","minutes",241)));write(c,"",r,1001);
        var d=write(c,"",create(m),0);long id=d.path("plan").path("id").asLong();write(c,"/"+id+"/publish",version(9),2600);
        d=write(c,"/"+id+"/publish",version(0),0);long ex=d.path("exercises").get(0).path("id").asLong();
        write(c,"/"+id+"/sessions",session(1,List.of(Map.of("exerciseId",ex,"status","SKIPPED","note","疲劳"))),1001);
        write(c,"/"+id+"/sessions",session(1,List.of(Map.of("exerciseId",ex,"status","DONE","sets",3,"reps",10),Map.of("exerciseId",ex,"status","DONE","sets",3,"reps",10))),1001);
        var s=session(1,List.of(Map.of("exerciseId",ex,"status","DONE","sets",3,"reps",10)));s.put("performedOn",LocalDate.now().plusDays(1));write(c,"/"+id+"/sessions",s,1001);
        m.setBlacklistFlag(true);members.saveAndFlush(m);write(c,"/"+id+"/archive",reason(1,"终止"),1003);
    }
    @Test void durationSnapshotsPartialSkippingAndSummaryIgnoreFiltersAndVoidedSessions() throws Exception {
        var c=account("COACH",0);var m=member(c);var r=create(m);r.put("exercises",List.of(Map.of("name","有氧","mode","DURATION","minutes",30),Map.of("name","深蹲","mode","STRENGTH","sets",3,"reps",12)));
        var d=write(c,"",r,0);long id=d.path("plan").path("id").asLong();d=write(c,"/"+id+"/publish",version(0),0);
        long first=d.path("exercises").get(0).path("id").asLong(),second=d.path("exercises").get(1).path("id").asLong();
        var rec=session(1,List.of(Map.of("exerciseId",first,"status","DONE","minutes",20),Map.of("exerciseId",second,"status","SKIPPED","note","调整训练")));d=write(c,"/"+id+"/sessions",rec,0);
        var snapshot=d.path("sessions").path("records").get(0);assertThat(snapshot.path("exercises").get(0).path("targetMinutes").asInt()).isEqualTo(30);assertThat(snapshot.path("exercises").get(0).path("minutes").asInt()).isEqualTo(20);assertThat(snapshot.path("bookingId").isNull()).isTrue();
        var filtered=read(c,"/workbench?status=ARCHIVED&keyword=nomatch",0);assertThat(filtered.path("plans").path("total").asInt()).isZero();assertThat(filtered.path("summary").path("activePlans").asInt()).isEqualTo(1);assertThat(filtered.path("summary").path("sessionsThisMonth").asInt()).isEqualTo(1);
        write(c,"/"+id+"/sessions/"+snapshot.path("id").asLong()+"/void",reason(0,"登记错误"),0);assertThat(read(c,"/workbench",0).path("summary").path("sessionsThisMonth").asInt()).isZero();
    }
    @Test void everyWriteRequiresKeyAndVersionAndMemberCannotAccessOthers() throws Exception {
        var c=account("COACH",0);var m=member(c);long id=write(c,"",create(m),0).path("plan").path("id").asLong();
        for(String endpoint:List.of("publish","archive","draft","sessions"))for(String field:List.of("expectedVersion","idempotencyKey")){var r=version(0);r.remove(field);mvc.perform(post("/api/v1/training-plans/"+id+"/"+endpoint).header("Authorization",token(c)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(r))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));}
        var owner=account("MEMBER",0);var foreign=member(c);foreign.setUserId(owner.getId());members.saveAndFlush(foreign);write(c,"/"+id+"/publish",version(0),0);memberRead(owner,"/"+id,1003);
        assertThat(memberRead(owner,"?status=ACTIVE",0).path("plans").path("total").asInt()).isZero();
    }
    @Test void fractionalIntegerFieldsCannotBeSilentlyTruncated() throws Exception {
        var c=account("COACH",0);var r=create(member(c));r.put("exercises",List.of(Map.of("name","深蹲","mode","STRENGTH","sets",2.5,"reps",10)));
        mvc.perform(post("/api/v1/training-plans").header("Authorization",token(c)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(r))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
        r=create(member(c));r.put("memberId",3.5);mvc.perform(post("/api/v1/training-plans").header("Authorization",token(c)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(r))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1001));
    }
    @Test void canonicalRetriesNormalizeDecimalSpellingsButDoNotNormalizeEnums() throws Exception {
        var c=account("COACH",0);var r=create(member(c));var d=write(c,"",r,0);
        r.put("exercises",List.of(Map.of("name","深蹲","mode","STRENGTH","sets",3,"reps",12,"weightKg",new java.math.BigDecimal("20.00"))));
        assertThat(write(c,"",r,0).path("plan").path("id")).isEqualTo(d.path("plan").path("id"));
        r.put("goal"," GENERAL ");write(c,"",r,2600);
    }
    @Test void v22PhysicalConstraintsAreAlsoCompatibleWithFreshH2() {
        var isolated=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:training-schema-"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        var sql=new org.springframework.jdbc.core.JdbcTemplate(isolated);
        for(String table:List.of("member","sys_user","venue","booking")){sql.execute("create table "+table+" (id bigint primary key)");sql.update("insert into "+table+"(id) values (1)");}
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("db/migration/V22__training_plans_and_sessions.sql")).execute(isolated);
        sql.update("insert into training_plan(member_id,coach_id,venue_id,title,goal,start_date,end_date,status,version,created_at,updated_at) values(1,1,1,'计划','GENERAL',CURRENT_DATE,CURRENT_DATE,'DRAFT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        org.assertj.core.api.Assertions.assertThatThrownBy(()->sql.update("insert into training_plan_exercise(plan_id,position,name,mode,sets,reps) values(1,1,'深蹲','STRENGTH',null,10)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->sql.update("update training_plan set status='ACTIVE',active_member_key='wrong' where id=1")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(sql.queryForObject("select count(*) from training_plan_event",Long.class)).isZero();
        sql.execute("shutdown");
    }
    @Test void pagedHistoryAndLatestHundredEventsExposeTotalWithoutOverReturning() throws Exception {
        var c=account("COACH",0);var m=member(c);var owner=account("MEMBER",0);m.setUserId(owner.getId());m.setTrainingGoal("ENDURANCE");members.saveAndFlush(m);
        var d=write(c,"",create(m),0);long id=d.path("plan").path("id").asLong();d=write(c,"/"+id+"/publish",version(0),0);long ex=d.path("exercises").get(0).path("id").asLong();
        d=write(c,"/"+id+"/sessions",session(1,List.of(Map.of("exerciseId",ex,"status","DONE","sets",2,"reps",8))),0);long sid=d.path("sessions").path("records").get(0).path("id").asLong();
        for(int i=0;i<101;i++){var f=version(i);f.put("rating",5);memberWrite(owner,"/"+id+"/sessions/"+sid+"/feedback",f,0);}
        d=read(c,"/"+id+"?sessionPage=2&sessionSize=1",0);assertThat(d.path("events").size()).isEqualTo(100);assertThat(d.path("eventTotal").asLong()).isEqualTo(104);assertThat(d.path("sessions").path("records").size()).isZero();assertThat(d.path("sessions").path("total").asLong()).isEqualTo(1);
        assertThat(members.findById(m.getId()).orElseThrow().getTrainingGoal()).isEqualTo("ENDURANCE");
        assertThat(memberRead(owner,"/"+id,0).path("eventTotal").asLong()).isZero();
    }
    @Test void draftRewriteAllowsSameNamesAndPublishesMaximum366DayCycle() throws Exception {
        var c=account("COACH",0);var r=create(member(c));var d=write(c,"",r,0);long id=d.path("plan").path("id").asLong();long old=d.path("exercises").get(0).path("id").asLong();
        r.remove("memberId");r.put("expectedVersion",0);r.put("idempotencyKey",key());r.put("startDate",LocalDate.now().minusDays(180));r.put("endDate",LocalDate.now().plusDays(185));r.put("title","新版草稿");
        r.put("exercises",List.of(Map.of("name","快走","mode","DURATION","minutes",10),Map.of("name","快走","mode","DURATION","minutes",20)));d=write(c,"/"+id+"/draft",r,0);
        assertThat(d.path("plan").path("version").asInt()).isEqualTo(1);assertThat(d.path("exercises").size()).isEqualTo(2);assertThat(d.path("exercises").get(0).path("id").asLong()).isNotEqualTo(old);
        assertThat(write(c,"/"+id+"/draft",r,0).path("plan").path("version").asInt()).isEqualTo(1);
        d=write(c,"/"+id+"/publish",version(1),0);assertThat(d.path("plan").path("status").asText()).isEqualTo("ACTIVE");
        r=create(member(c));r.put("startDate",LocalDate.now().minusDays(180));r.put("endDate",LocalDate.now().plusDays(186));write(c,"",r,1001);
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
