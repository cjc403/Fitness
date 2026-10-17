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
 final ObjectMapper json=new ObjectMapper();
 @Test void confirmedBookingMustStillExistWithTheExpectedIdAndStatus(){
  var business=mock(AgentBusiness.class);when(business.query(eq("bookings"),any())).thenReturn(new AgentDtos.ToolResult("bookings","预约",json.valueToTree(List.of(Map.of("id",2,"status","BOOKED"))),"db",LocalDateTime.now(),false));
  var v=new AgentDomainVerifier(business,mock(TrainingPlanService.class),mock(LeadService.class),mock(MemberService.class),json,mock(com.gym.fitness.module.workout.service.TrainingPlanAccess.class),mock(com.gym.fitness.module.member.service.AdvisorService.class),mock(com.gym.fitness.module.course.service.CourseService.class),mock(com.gym.fitness.module.member.service.MemberPortalService.class));
  var r=new AgentMutationReceiptService.Receipt("r","t",1,"POST","/api/v1/member/bookings","SUCCEEDED",json.createObjectNode(),json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("id",1,"status","BOOKED"))),LocalDateTime.now());
  assertFalse(v.verify("booking",r,"帮我预约",true));
 }
 @Test void memberVoidResponseIsVerifiedThroughTheActualScopedBooking(){
  var business=mock(AgentBusiness.class);when(business.query(eq("bookings"),any())).thenReturn(new AgentDtos.ToolResult("bookings","预约",json.valueToTree(List.of(Map.of("id",2,"scheduleId",7,"status","BOOKED"))),"db",LocalDateTime.now(),false));
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
