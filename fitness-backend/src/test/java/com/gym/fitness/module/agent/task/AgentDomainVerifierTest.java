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
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 @Test void aDraftIsNotCompleteWhenTheSavedExercisesDifferFromTheFrozenRequest(){
  var training=mock(TrainingPlanService.class);
  var plan=Map.of("id",2,"title","基础训练","memberId",7,"status","DRAFT");
  var detail=json.convertValue(Map.of("plan",plan,"exercises",List.of(Map.of("name","卧推","mode","STRENGTH","sets",3,"reps",10)),"sessions",Map.of("records",List.of(),"total",0,"page",1,"size",20),"events",List.of()),com.gym.fitness.module.workout.dto.TrainingPlanDtos.Detail.class);
  when(training.detail(2L,1,20,false)).thenReturn(detail);
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),training,mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class));
  var request=json.valueToTree(Map.of("memberId",7,"title","基础训练","exercises",List.of(Map.of("name","深蹲","mode","STRENGTH","sets",3,"reps",10))));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/training-plans","SUCCEEDED",request,json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("plan",plan))),LocalDateTime.now());
  assertFalse(v.verify("training",r,"为学员创建训练计划草稿，标题基础训练，深蹲3组每组10次",false));
  var correct=json.convertValue(Map.of("plan",plan,"exercises",request.get("exercises"),"sessions",Map.of("records",List.of(),"total",0,"page",1,"size",20),"events",List.of()),com.gym.fitness.module.workout.dto.TrainingPlanDtos.Detail.class);when(training.detail(2L,1,20,false)).thenReturn(correct);
  assertTrue(v.verify("training",r,"为学员创建训练计划草稿，标题基础训练，深蹲3组每组10次",false));
 }
 @Test void matchingFeedbackRatingDoesNotHideWrongFeedbackContent()throws Exception{
  var training=mock(TrainingPlanService.class);var plan=Map.of("id",2,"title","基础训练","memberId",7);
  var detail=json.convertValue(Map.of("plan",plan,"exercises",List.of(),"sessions",Map.of("records",List.of(Map.of("id",9,"feedbackRating",5,"feedbackContent","错误内容")),"total",1,"page",1,"size",20),"events",List.of()),com.gym.fitness.module.workout.dto.TrainingPlanDtos.Detail.class);when(training.detail(2L,1,20,true)).thenReturn(detail);
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),training,mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class));
  String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes("预期内容")));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/member/training-plans/2/sessions/9/feedback","SUCCEEDED",json.valueToTree(Map.of("rating",5,"contentHash",hash)),json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("plan",plan))),LocalDateTime.now());assertFalse(v.verify("training",r,"为训练计划#2中训练记录#9提交反馈，评分5，内容预期内容",true));
 }
 @Test void aSessionNumberCannotAuthorizeAWrongTrainingPlan(){
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class));
  assertThrows(RuntimeException.class,()->v.guard("为训练计划#2的训练记录#1填写反馈",json.valueToTree(Map.of("kind","training","id",1,"name","其他计划")),json.createObjectNode()));
  assertDoesNotThrow(()->v.guard("为训练计划#2的训练记录#1填写反馈",json.valueToTree(Map.of("kind","training","id",2,"name","正确计划")),json.createObjectNode()));
 }
 @Test void confirmedBookingMustStillExistWithTheExpectedIdAndStatus(){
  var business=mock(AgentBusiness.class);when(business.bookingEvidence(anyLong(),anyLong())).thenReturn(json.valueToTree(Map.of("id",2,"status","BOOKED")));
  var v=new AgentDomainVerifier(business,mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/member/bookings","SUCCEEDED",json.createObjectNode(),json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("id",1,"status","BOOKED"))),LocalDateTime.now());
  assertFalse(v.verify("booking",r,"帮我预约",true));
 }
 @Test void memberVoidResponseIsVerifiedThroughTheActualScopedBooking(){
  var business=mock(AgentBusiness.class);when(business.bookingEvidence(anyLong(),anyLong())).thenReturn(json.valueToTree(Map.of("id",2,"scheduleId",7,"status","BOOKED")));
  var v=new AgentDomainVerifier(business,mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/member/bookings","SUCCEEDED",json.valueToTree(Map.of("scheduleId",7)),json.createObjectNode(),json.valueToTree(Map.of("code",0)),LocalDateTime.now());
  assertTrue(v.verify("booking",r,"帮我预约",true));
 }
 @Test void wrongMemberAndWrongCourseAreRejectedBeforeApproval(){
  var v=new AgentDomainVerifier(mock(AgentBusiness.class),mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class));
  assertThrows(RuntimeException.class,()->v.guard("为会员张三预约流瑜伽",json.valueToTree(Map.of("kind","booking","memberId",2,"memberName","李四","scheduleId",7,"courseName","流瑜伽")),json.createObjectNode()));
  assertThrows(RuntimeException.class,()->v.guard("为会员张三预约流瑜伽",json.valueToTree(Map.of("kind","booking","memberId",2,"memberName","张三","scheduleId",7,"courseName","力量")),json.createObjectNode()));
  assertDoesNotThrow(()->v.guard("为会员张三预约流瑜伽",json.valueToTree(Map.of("kind","booking","memberId",2,"memberName","张三","scheduleId",7,"courseName","流瑜伽")),json.createObjectNode()));
 }
}
