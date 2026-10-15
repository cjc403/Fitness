package com.gym.fitness;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.auth.entity.SysUserEntity;
import com.gym.fitness.module.auth.repository.SysUserRepository;
import com.gym.fitness.module.member.entity.MemberEntity;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.*;
import com.gym.fitness.module.course.entity.*;
import com.gym.fitness.module.course.repository.*;
import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:agent;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=ROLE", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "app.ai.enabled=false", "fitness.operations.enabled=false"})
@AutoConfigureMockMvc
class AgentIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired SysUserRepository users; @Autowired RoleRepository roles; @Autowired VenueRepository venues;
 @Autowired MemberRepository members; @Autowired JwtTokenProvider jwt; @Autowired CourseRepository courses; @Autowired CourseScheduleRepository schedules;
 @Autowired BookingRepository bookings; @Autowired JdbcTemplate jdbc;
 @Autowired com.gym.fitness.module.lead.repository.SalesLeadRepository leadRepository;
 @Autowired com.gym.fitness.module.agent.AgentBusiness agentBusiness;
 @Test void sixRolesHaveDistinctToolsAndNoFinancialExecution() throws Exception {
  for(String role:List.of("SUPER_ADMIN","MANAGER","RECEPTION","COACH","SALES","MEMBER")){
   var u=account(role,0);var d=get(u,"/profile",0);assertThat(d.path("role").asText()).isEqualTo(role);
   var names=new ArrayList<String>();d.path("tools").forEach(t->names.add(t.path("name").asText()));
   assertThat(names).contains("courses");assertThat(names).doesNotContain("refund","payment","execute_sql");
   assertThat(names.contains("training_draft")).isEqualTo(role.equals("COACH"));
   assertThat(names.contains("book_trial")).isEqualTo(Set.of("SALES","MANAGER","SUPER_ADMIN").contains(role));
   assertThat(names.contains("analytics")).isEqualTo(Set.of("MANAGER","SUPER_ADMIN").contains(role));
  }
 }
 @Test void memberRejectsForeignToolAndInjectedArguments() throws Exception {
  var u=account("MEMBER",0);post(u,"/query",tool("analytics",Map.of()),1003);
  post(u,"/query",tool("courses",Map.of("role","SUPER_ADMIN")),1001);
  post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",1,"memberId",999)),1001);
 }
 @Test void previewDoesNotWriteConfirmIsReplaySafeAndSessionBound() throws Exception {
  var u=account("MEMBER",0);var m=member(u);var s=schedule(u,3);long before=bookings.count();
  var a=post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId())),0);String id=a.path("id").asText();
  assertThat(a.path("status").asText()).isEqualTo("PENDING");assertThat(bookings.count()).isEqualTo(before);
  postSession(u,"another-session","/actions/"+id+"/confirm",Map.of(),1003);
  var done=post(u,"/actions/"+id+"/confirm",Map.of(),0);assertThat(done.path("status").asText()).isEqualTo("SUCCEEDED");
  assertThat(done.path("result").path("id").asLong()).isPositive();assertThat(bookings.count()).isEqualTo(before+1);
  assertThat(post(u,"/actions/"+id+"/confirm",Map.of(),0).path("result")).isEqualTo(done.path("result"));
  assertThat(bookings.count()).isEqualTo(before+1);
  var other=account("MEMBER",0);member(other);post(other,"/actions/"+id+"/confirm",Map.of(),1003);
 }
 @Test void cancellationExpiryAndConfirmationParameterInjectionCannotWrite() throws Exception {
  var u=account("MEMBER",0);member(u);var s=schedule(u,3);long n=bookings.count();
  var a=post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId())),0);String id=a.path("id").asText();
  post(u,"/actions/"+id+"/confirm",Map.of("scheduleId",999),1001);
  assertThat(post(u,"/actions/"+id+"/cancel",Map.of(),0).path("status").asText()).isEqualTo("CANCELLED");
  post(u,"/actions/"+id+"/confirm",Map.of(),2600);
  a=post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId())),0);id=a.path("id").asText();
  jdbc.update("update agent_action set expires_at=? where id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),id);
  post(u,"/actions/"+id+"/confirm",Map.of(),2600);assertThat(bookings.count()).isEqualTo(n);
 }
 @Test void businessFailureIsAuditedWithoutSuccessOrPartialReservation() throws Exception {
  var u=account("MEMBER",0);member(u);var s=schedule(u,1);String id=post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId())),0).path("id").asText();
  s.setEnrolledCount(1);schedules.saveAndFlush(s);long n=bookings.count();
  var result=post(u,"/actions/"+id+"/confirm",Map.of(),0);assertThat(result.path("status").asText()).isEqualTo("FAILED");
  assertThat(result.path("error").asText()).isNotBlank();assertThat(bookings.count()).isEqualTo(n);
  assertThat(schedules.findById(s.getId()).orElseThrow().getEnrolledCount()).isEqualTo(1);
 }
 @Test void roleAndVenueChangeInvalidatePreparedAction() throws Exception {
  var u=account("RECEPTION",0);var m=member(u);var s=schedule(u,3);
  String id=post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId(),"memberId",m.getId())),0).path("id").asText();
  var v=venues.findAll().get(1);u.setVenueId(v.getId());u.setVenue(v);users.saveAndFlush(u);
  post(u,"/actions/"+id+"/confirm",Map.of(),1003);
 }
 @Test void crossVenuePreviewIsDeniedAndLocalChatIsHonest() throws Exception {
  var u=account("RECEPTION",0);var other=account("MEMBER",1);var m=member(other);var s=schedule(other,3);
  post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId(),"memberId",m.getId())),1003);
  var d=post(u,"/chat",Map.of("message","今天有哪些课程？"),0);assertThat(d.path("mode").asText()).isEqualTo("LOCAL");
  assertThat(d.path("results").isArray()).isTrue();assertThat(d.path("actions").size()).isZero();
 }
 @Test void coachCreatesOnlyDraftWithConfirmedContent() throws Exception {
  var u=account("COACH",0);var m=member(u);
  var args=new LinkedHashMap<String,Object>();args.put("memberId",m.getId());args.put("title","确认后的基础训练");args.put("goal","GENERAL");args.put("startDate",LocalDate.now().toString());args.put("endDate",LocalDate.now().plusDays(14).toString());
  args.put("exercises",List.of(Map.of("name","划船","mode","STRENGTH","sets",3,"reps",12)));
  String id=post(u,"/actions/preview",tool("training_draft",args),0).path("id").asText();
  var done=post(u,"/actions/"+id+"/confirm",Map.of(),0);assertThat(done.path("status").asText()).isEqualTo("SUCCEEDED");
  assertThat(done.path("result").path("plan").path("status").asText()).isEqualTo("DRAFT");
 }
 @Test void movedScheduleRequiresNewPreviewRatherThanBookingChangedTime() throws Exception {
  var u=account("MEMBER",0);member(u);var s=schedule(u,3);long n=bookings.count();
  String id=post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId())),0).path("id").asText();
  s.setScheduledStartTime(s.getScheduledStartTime().plusHours(1));s.setScheduledEndTime(s.getScheduledEndTime().plusHours(1));schedules.saveAndFlush(s);
  assertThat(post(u,"/actions/"+id+"/confirm",Map.of(),0).path("status").asText()).isEqualTo("FAILED");assertThat(bookings.count()).isEqualTo(n);
 }
 @Test void oldCancelCardCannotCancelNewBookingReusingSameBookingId() throws Exception {
  var u=account("MEMBER",0);member(u);var s=schedule(u,3);
  String create=post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId())),0).path("id").asText();
  long booking=post(u,"/actions/"+create+"/confirm",Map.of(),0).path("result").path("id").asLong();
  String old=post(u,"/actions/preview",tool("cancel_booking",Map.of("bookingId",booking)),0).path("id").asText();
  String now=post(u,"/actions/preview",tool("cancel_booking",Map.of("bookingId",booking)),0).path("id").asText();
  assertThat(post(u,"/actions/"+now+"/confirm",Map.of(),0).path("status").asText()).isEqualTo("SUCCEEDED");
  create=post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId())),0).path("id").asText();
  assertThat(post(u,"/actions/"+create+"/confirm",Map.of(),0).path("result").path("id").asLong()).isEqualTo(booking);
  assertThat(post(u,"/actions/"+old+"/confirm",Map.of(),0).path("status").asText()).isEqualTo("FAILED");assertThat(bookings.findById(booking).orElseThrow().getStatus()).isEqualTo("BOOKED");
 }
 @Test void headquartersCanSwitchVenueAndOldVenueCardCannotBeConfirmed() throws Exception {
  var u=account("SUPER_ADMIN",0);var m=member(u);var s=schedule(u,3);
  String id=post(u,"/actions/preview",tool("book_course",Map.of("scheduleId",s.getId(),"memberId",m.getId())),0).path("id").asText();
  var other=venues.findAll().get(1);String token="Bearer "+jwt.generateToken(u.getId(),u.getUsername(),"SUPER_ADMIN",other.getId(),u.currentSessionVersion(),UUID.nameUUIDFromBytes(("session-"+u.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/agent/profile").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/agent/actions/"+id+"/confirm").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1003));
 }
 @Test void salesTrialHasRealVersionedReceiptAndReplayDoesNotDuplicate() throws Exception {
  var u=account("SALES",0);var lead=lead(u);var start=LocalDateTime.now().plusDays(5).withNano(0);var args=Map.of("leadId",lead.getId(),"expectedVersion",0,"hostId",u.getId(),"startsAt",start.toString(),"endsAt",start.plusHours(1).toString(),"content","器械入门和门店参观");
  String id=post(u,"/actions/preview",tool("book_trial",args),0).path("id").asText();assertThat(leadRepository.findById(lead.getId()).orElseThrow().getVersion()).isZero();
  var done=post(u,"/actions/"+id+"/confirm",Map.of(),0);assertThat(done.path("status").asText()).isEqualTo("SUCCEEDED");assertThat(done.path("result").path("lead").path("stage").asText()).isEqualTo("TRIAL_BOOKED");assertThat(done.path("result").path("trials").size()).isEqualTo(1);
  assertThat(post(u,"/actions/"+id+"/confirm",Map.of(),0).path("result")).isEqualTo(done.path("result"));post(u,"/actions/preview",tool("book_trial",args),1001);
 }
 @Test void advisorCannotQueryOrArrangeOtherAdvisorsLead() throws Exception {
  var u=account("SALES",0);var other=account("SALES",0);var l=lead(other);
  post(u,"/query",tool("leads",Map.of("leadId",l.getId())),1003);var start=LocalDateTime.now().plusDays(4).withNano(0);
  post(u,"/actions/preview",tool("book_trial",Map.of("leadId",l.getId(),"expectedVersion",0,"hostId",u.getId(),"startsAt",start.toString(),"endsAt",start.plusHours(1).toString(),"content","体验")),1003);
 }
 @Test void modelContextOmitsPersonalNamesAndFreeNotesButKeepsBusinessIds() {
  var data=json.valueToTree(Map.of("records",List.of(Map.of("id",13800000000L,"realName","隐私姓名","phone","13800000000","note","不应外发的完整记录","lastFollowContent","回访私密内容","events",List.of(Map.of("content","完整事件记录")),"status","ACTIVE"))));
  var context=json.valueToTree(agentBusiness.modelContext(new com.gym.fitness.module.agent.AgentDtos.ToolResult("members","会员",data,"隔离库",LocalDateTime.now(),false)));
  assertThat(context.toString()).doesNotContain("隐私姓名","13800000000\"","完整记录","回访私密内容","完整事件记录");assertThat(context.path("data").path("records").get(0).path("id").asLong()).isEqualTo(13800000000L);
  var list=agentBusiness.clean(java.util.stream.IntStream.range(0,21).boxed().toList());assertThat(list.truncated()).isTrue();assertThat(list.data().size()).isEqualTo(20);
 }
 com.gym.fitness.module.lead.entity.SalesLeadEntity lead(SysUserEntity u){var l=new com.gym.fitness.module.lead.entity.SalesLeadEntity();l.setName("隔离体验潜客");l.setPhone("19"+String.format("%09d",u.getId()));l.setSource("WALK_IN");l.setIntent("HIGH");l.setOwnerId(u.getId());l.setVenueId(u.getVenueId());l.setStage("CONSULTING");l.setCreatedAt(LocalDateTime.now());l.setUpdatedAt(LocalDateTime.now());return leadRepository.saveAndFlush(l);}
 Map<String,Object> tool(String name,Map<String,?> args){return Map.of("tool",name,"arguments",args);}
 JsonNode get(SysUserEntity u,String path,int code)throws Exception{return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/agent"+path).header("Authorization",token(u,"session-"+u.getId()))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
 JsonNode post(SysUserEntity u,String path,Object body,int code)throws Exception{return postSession(u,"session-"+u.getId(),path,body,code);}
 JsonNode postSession(SysUserEntity u,String session,String path,Object body,int code)throws Exception{return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/agent"+path).header("Authorization",token(u,session)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code)).andReturn().getResponse().getContentAsString()).path("data");}
 SysUserEntity account(String role,int i){var u=new SysUserEntity();var r=roles.findByCode(role).orElseThrow();var v=venues.findAll().get(i);u.setUsername("agent-"+UUID.randomUUID());u.setPasswordHash("isolated-unused");u.setRealName("隔离"+role);u.setRoleId(r.getId());u.setRole(r);u.setVenueId(v.getId());u.setVenue(v);u.setCoach(role.equals("COACH"));u.setStatus("ACTIVE");return users.saveAndFlush(u);}
 MemberEntity member(SysUserEntity u){var m=new MemberEntity();m.setMemberNo("AG-"+UUID.randomUUID());m.setRealName("隔离学员");m.setPhone("13800000000");m.setVenueId(u.getVenueId());m.setCoachId(u.getId());m.setSalesUserId(u.getId());m.setUserId(u.getId());m.setStatus("ACTIVE");m.setBlacklistFlag(false);return members.saveAndFlush(m);}
 CourseScheduleEntity schedule(SysUserEntity u,int capacity){var c=new CourseEntity();c.setName("隔离普拉提");c.setType("REGULAR");c.setVenueId(u.getVenueId());courses.saveAndFlush(c);var s=new CourseScheduleEntity();s.setCourseId(c.getId());s.setCourse(c);s.setVenueId(u.getVenueId());s.setCoachId(u.getId());s.setScheduledStartTime(LocalDateTime.now().plusDays(2));s.setScheduledEndTime(LocalDateTime.now().plusDays(2).plusHours(1));s.setMaxStudents(capacity);s.setEnrolledCount(0);s.setStatus("SCHEDULED");return schedules.saveAndFlush(s);}
 String token(SysUserEntity u,String session){return "Bearer "+jwt.generateToken(u.getId(),u.getUsername(),u.getRole().getCode(),u.getVenueId(),u.currentSessionVersion(),UUID.nameUUIDFromBytes(session.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());}
}
