package com.gym.fitness.module.agent.task;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.module.agent.*;
import com.gym.fitness.module.workout.service.TrainingPlanService;
import com.gym.fitness.module.lead.service.LeadService;
import com.gym.fitness.module.member.service.MemberService;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AgentDomainVerifierTest {
 @Test void creatingALeadCannotDropTheRequestedVenueBinding(){
  var venues=mock(com.gym.fitness.module.organization.repository.VenueRepository.class);
  var a=new com.gym.fitness.module.organization.entity.VenueEntity();a.setId(1L);a.setName("翠湖店");var b=new com.gym.fitness.module.organization.entity.VenueEntity();b.setId(2L);b.setName("滨江店");when(venues.findAll()).thenReturn(List.of(a,b));
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),venues);
  var me=new com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session(1L,"SUPER_ADMIN",1L,"s","/dashboard",List.of("LEAD_WRITE"),null);
  var body=json.valueToTree(Map.of("name","小李","venueId",2));var before=v.before(me,"POST","/api/v1/leads",body);
  assertThrows(RuntimeException.class,()->v.guard("为翠湖店创建潜客小李",before,body));
  assertDoesNotThrow(()->v.guard("为滨江店创建潜客小李",before,body));
 }
 @Test void anExistingBookingSnapshotBindsItsActualMemberVenueAndStartTime(){
  var business=mock(AgentBusiness.class);when(business.bookingEvidence(8L,0L)).thenReturn(json.valueToTree(Map.of("id",8,"memberId",3,"scheduleId",7,"member",Map.of("realName","张三"),"schedule",Map.of("venueId",1,"scheduledStartTime","2026-10-20T15:00:00","course",Map.of("name","流瑜伽")))));
  var v=new AgentDomainVerifier(business,mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  var me=new com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session(1L,"RECEPTION",1L,"s","/members",List.of("BOOKING_WRITE"),null);var before=v.before(me,"PUT","/api/v1/admin/bookings/8/cancel",json.createObjectNode());assertEquals(3,before.path("memberId").asLong());assertEquals("2026-10-20T15:00:00",before.path("startTime").asText());
  assertThrows(RuntimeException.class,()->v.guard("取消会员编号#2的预约编号#8",before,json.createObjectNode()));
 }
 @Test void aProfileReceiptMustProveTheNewIntentValueNotJustTheSameLead(){
  var leads=mock(LeadService.class);when(leads.detail(2L)).thenReturn(Map.of("lead",Map.of("id",2,"name","小李","venueId",1,"intent","LOW")));
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),mock(TrainingPlanService.class),leads,mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/leads/2/profile","SUCCEEDED",json.valueToTree(Map.of("intent","HIGH")),json.valueToTree(Map.of("kind","lead","id",2,"name","小李")),json.valueToTree(Map.of("code",0,"data",Map.of("lead",Map.of("id",2,"name","小李","venueId",1,"intent","HIGH")))),LocalDateTime.now());assertFalse(v.verify("lead",r,"修改潜客编号#2的意向为高",false));
 }
 @Test void feedbackOnARecordBeyondTheFirstTwentyCanStillBeVerified(){
  var training=mock(TrainingPlanService.class);var plan=Map.of("id",2,"title","基础训练","memberId",7,"status","ACTIVE");
  var old=Map.of("id",99,"planId",2,"performedOn","2026-10-01","status","COMPLETED","feedbackRating",5,"exercises",List.of());
  var first=json.convertValue(Map.of("plan",plan,"exercises",List.of(),"sessions",Map.of("records",List.of(),"total",21,"page",1,"size",20),"events",List.of()),com.gym.fitness.module.workout.dto.TrainingPlanDtos.Detail.class);
  var second=json.convertValue(Map.of("plan",plan,"exercises",List.of(),"sessions",Map.of("records",List.of(old),"total",21,"page",2,"size",20),"events",List.of()),com.gym.fitness.module.workout.dto.TrainingPlanDtos.Detail.class);when(training.detail(2L,1,20,true)).thenReturn(first);when(training.detail(2L,2,20,true)).thenReturn(second);when(training.sessionEvidence(2L,99L,true)).thenReturn(second.sessions().records().get(0));
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),training,mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/member/training-plans/2/sessions/99/feedback","SUCCEEDED",json.valueToTree(Map.of("rating",5)),json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("plan",plan))),LocalDateTime.now());assertTrue(v.verify("training",r,"为训练计划#2中训练记录#99提交反馈，评分5",true));
 }
 @Test void explicitVenueAndSameNameAmbiguityAreCheckedBeforeAWritePermit(){
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  var before=json.valueToTree(Map.of("kind","lead","id",2,"name","小李","venueId",2,"venueName","滨江店","sameNameCount",2,"knownVenues",List.of(Map.of("id",1,"name","翠湖店"),Map.of("id",2,"name","滨江店"))));
  assertThrows(RuntimeException.class,()->v.guard("为翠湖店潜客小李登记电话跟进",before,json.createObjectNode()));assertThrows(RuntimeException.class,()->v.guard("为潜客小李登记电话跟进",before,json.createObjectNode()));
 }
 @Test void bookingTimeIsBoundEvenWhenTheMemberAndCourseNameAreCorrect(){
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  assertThrows(RuntimeException.class,()->v.guard("为会员张三预约流瑜伽，时间2026-10-20 14:00",json.valueToTree(Map.of("kind","booking","memberId",2,"memberName","张三","scheduleId",7,"courseName","流瑜伽","startTime","2026-10-20T15:00:00")),json.createObjectNode()));
 }
 @Test void aFollowUpReceiptCannotProveARevisedProfileIntentChange(){
  var leads=mock(LeadService.class);var actual=Map.of("lead",Map.of("id",2,"name","小李","venueId",1,"intent","LOW"));when(leads.detail(2L)).thenReturn(new java.util.HashMap<>(actual));
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),mock(TrainingPlanService.class),leads,mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/leads/2/follow-ups","SUCCEEDED",json.valueToTree(Map.of("outcome","INTERESTED")),json.valueToTree(Map.of("kind","lead","id",2,"name","小李")),json.valueToTree(Map.of("code",0,"data",actual)),LocalDateTime.now());
  assertFalse(v.verify("lead",r,"修改潜客编号#2的意向为高",false));
 }
 @Test void aTrainingRecordNeedsANewSessionWithTheFrozenExerciseValues(){
  var mapper=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);var training=mock(TrainingPlanService.class);
  var plan=Map.of("id",2,"title","基础训练","memberId",7,"status","ACTIVE");
  var request=mapper.valueToTree(Map.of("performedOn","2026-10-18","durationMinutes",30,"exercises",List.of(Map.of("exerciseId",8,"status","DONE","sets",3,"reps",10))));
  for(int sets:List.of(2,3)){
   var row=Map.of("id",9,"performedOn","2026-10-18","durationMinutes",30,"status","COMPLETED","exercises",List.of(Map.of("exerciseId",8,"status","DONE","sets",sets,"reps",10)));
   var detail=mapper.convertValue(Map.of("plan",plan,"exercises",List.of(),"sessions",Map.of("records",List.of(row),"total",1,"page",1,"size",20),"events",List.of()),com.gym.fitness.module.workout.dto.TrainingPlanDtos.Detail.class);when(training.detail(2L,1,20,false)).thenReturn(detail);if(!detail.sessions().records().isEmpty())when(training.sessionEvidence(2L,9L,false)).thenReturn(detail.sessions().records().get(0));
   var v=new AgentDomainVerifier(mock(AgentBusiness.class),training,mock(LeadService.class),mock(MemberService.class),mapper,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
   var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/training-plans/2/sessions","SUCCEEDED",request,mapper.valueToTree(Map.of("sessionHighWatermark",0)),mapper.valueToTree(Map.of("code",0,"data",Map.of("plan",plan,"recordedSessionId",9,"sessions",Map.of("records",List.of(row))))),LocalDateTime.now());
   assertEquals(sets==3,v.verify("training",r,"为训练计划#2登记今天训练",false));
  }
 }
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 @Test void aDraftIsNotCompleteWhenTheSavedExercisesDifferFromTheFrozenRequest(){
  var training=mock(TrainingPlanService.class);
  var plan=Map.of("id",2,"title","基础训练","memberId",7,"status","DRAFT");
  var detail=json.convertValue(Map.of("plan",plan,"exercises",List.of(Map.of("name","卧推","mode","STRENGTH","sets",3,"reps",10)),"sessions",Map.of("records",List.of(),"total",0,"page",1,"size",20),"events",List.of()),com.gym.fitness.module.workout.dto.TrainingPlanDtos.Detail.class);
  when(training.detail(2L,1,20,false)).thenReturn(detail);if(!detail.sessions().records().isEmpty())when(training.sessionEvidence(2L,9L,false)).thenReturn(detail.sessions().records().get(0));
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),training,mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  var request=json.valueToTree(Map.of("memberId",7,"title","基础训练","exercises",List.of(Map.of("name","深蹲","mode","STRENGTH","sets",3,"reps",10))));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/training-plans","SUCCEEDED",request,json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("plan",plan))),LocalDateTime.now());
  assertFalse(v.verify("training",r,"为学员创建训练计划草稿，标题基础训练，深蹲3组每组10次",false));
  var correct=json.convertValue(Map.of("plan",plan,"exercises",request.get("exercises"),"sessions",Map.of("records",List.of(),"total",0,"page",1,"size",20),"events",List.of()),com.gym.fitness.module.workout.dto.TrainingPlanDtos.Detail.class);when(training.detail(2L,1,20,false)).thenReturn(correct);
  assertTrue(v.verify("training",r,"为学员创建训练计划草稿，标题基础训练，深蹲3组每组10次",false));
 }
 @Test void matchingFeedbackRatingDoesNotHideWrongFeedbackContent()throws Exception{
  var training=mock(TrainingPlanService.class);var plan=Map.of("id",2,"title","基础训练","memberId",7);
  var detail=json.convertValue(Map.of("plan",plan,"exercises",List.of(),"sessions",Map.of("records",List.of(Map.of("id",9,"feedbackRating",5,"feedbackContent","错误内容")),"total",1,"page",1,"size",20),"events",List.of()),com.gym.fitness.module.workout.dto.TrainingPlanDtos.Detail.class);when(training.detail(2L,1,20,true)).thenReturn(detail);if(!detail.sessions().records().isEmpty())when(training.sessionEvidence(2L,9L,true)).thenReturn(detail.sessions().records().get(0));
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),training,mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes("预期内容")));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/member/training-plans/2/sessions/9/feedback","SUCCEEDED",json.valueToTree(Map.of("rating",5,"contentHash",hash)),json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("plan",plan))),LocalDateTime.now());assertFalse(v.verify("training",r,"为训练计划#2中训练记录#9提交反馈，评分5，内容预期内容",true));
 }
 @Test void aSessionNumberCannotAuthorizeAWrongTrainingPlan(){
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  assertThrows(RuntimeException.class,()->v.guard("为训练计划#2的训练记录#1填写反馈",json.valueToTree(Map.of("kind","training","id",1,"name","其他计划")),json.createObjectNode()));
  assertDoesNotThrow(()->v.guard("为训练计划#2的训练记录#1填写反馈",json.valueToTree(Map.of("kind","training","id",2,"name","正确计划")),json.createObjectNode()));
 }
 @Test void confirmedBookingMustStillExistWithTheExpectedIdAndStatus(){
  var business=mock(AgentBusiness.class);when(business.bookingEvidence(anyLong(),anyLong())).thenReturn(json.valueToTree(Map.of("id",2,"status","BOOKED")));
  var v=new AgentDomainVerifier(business,mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/member/bookings","SUCCEEDED",json.createObjectNode(),json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("id",1,"status","BOOKED"))),LocalDateTime.now());
  assertFalse(v.verify("booking",r,"帮我预约",true));
 }
 @Test void memberVoidResponseIsVerifiedThroughTheActualScopedBooking(){
  var business=mock(AgentBusiness.class);when(business.bookingEvidence(anyLong(),anyLong())).thenReturn(json.valueToTree(Map.of("id",2,"scheduleId",7,"status","BOOKED")));
  var v=new AgentDomainVerifier(business,mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/member/bookings","SUCCEEDED",json.valueToTree(Map.of("scheduleId",7)),json.createObjectNode(),json.valueToTree(Map.of("code",0)),LocalDateTime.now());
  assertTrue(v.verify("booking",r,"帮我预约",true));
 }
 @Test void wrongMemberAndWrongCourseAreRejectedBeforeApproval(){
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class),mock(com.gym.fitness.module.organization.repository.VenueRepository.class));
  assertThrows(RuntimeException.class,()->v.guard("为会员张三预约流瑜伽",json.valueToTree(Map.of("kind","booking","memberId",2,"memberName","李四","scheduleId",7,"courseName","流瑜伽")),json.createObjectNode()));
  assertThrows(RuntimeException.class,()->v.guard("为会员张三预约流瑜伽",json.valueToTree(Map.of("kind","booking","memberId",2,"memberName","张三","scheduleId",7,"courseName","力量")),json.createObjectNode()));
  assertDoesNotThrow(()->v.guard("为会员张三预约流瑜伽",json.valueToTree(Map.of("kind","booking","memberId",2,"memberName","张三","scheduleId",7,"courseName","流瑜伽")),json.createObjectNode()));
 }
}
