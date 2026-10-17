package com.gym.fitness.module.agent.task;
import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.agent.computer.*;
import com.gym.fitness.module.course.service.CourseService;
import com.gym.fitness.module.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentOutcomeVerifierTest {
 @Test void aPagedQueryNeedsEveryDistinctRowAndRejectsMissingOrDuplicatePages(){
  var first=Map.of("tool","bookings","arguments",Map.of("page",1),"asOf",LocalDateTime.now().toString(),"truncated",true,"pagination",Map.of("page",1,"size",20,"total",21,"returned",20,"keys",java.util.stream.IntStream.rangeClosed(1,20).mapToObj(i->"id:"+i).toList(),"contentTruncated",false));
  for(var last:List.of(Map.of("page",2,"size",20,"total",21,"returned",1,"keys",List.of("id:21"),"contentTruncated",false),Map.of("page",2,"size",20,"total",21,"returned",1,"keys",List.of("id:1"),"contentTruncated",false),Map.of("page",2,"size",20,"total",21,"returned",1,"keys",List.of("id:21"),"contentTruncated",true))){
   var second=Map.of("tool","bookings","arguments",Map.of("page",2),"asOf",LocalDateTime.now().toString(),"truncated",true,"pagination",last);
   var checkpoint=json.createObjectNode();checkpoint.set("queryProofs",json.valueToTree(List.of(first,second)));var task=new AgentTaskStore.View("id",0,"查询预约","RUNNING",json.createObjectNode(),checkpoint,LocalDateTime.now(),LocalDateTime.now());when(store.check(me,"id",0,"document")).thenReturn(task);when(receipts.list(me,"id")).thenReturn(List.of());
   boolean complete=new AgentOutcomeVerifier(store,receipts,computer,courses,chat,json,mock(AgentDomainVerifier.class)).verify(me,"id",0,"document",json.createObjectNode()).complete();assertEquals(last.get("keys").equals(List.of("id:21"))&&Boolean.FALSE.equals(last.get("contentTruncated")),complete);
  }
 }
 @Test void ordinaryBookingCheckInAndTrainingRecordWordingUsesActualMutationEvidence(){
  for(var spec:List.of(List.of("为会员张三预约流瑜伽","/api/v1/admin/bookings","booking"),List.of("为预约编号#2签到","/api/v1/admin/bookings/2/check-in","booking"),List.of("为计划编号#2登记今天的训练","/api/v1/training-plans/2/sessions","training"))){
   var task=new AgentTaskStore.View("id",0,spec.get(0),"RUNNING",json.createObjectNode(),json.createObjectNode(),LocalDateTime.now(),LocalDateTime.now());when(store.check(me,"id",0,"document")).thenReturn(task);
   var receipt=new AgentMutationReceiptService.Receipt("r","id",0,"POST",spec.get(1),"SUCCEEDED",json.createObjectNode(),json.createObjectNode(),json.valueToTree(Map.of("code",0)),LocalDateTime.now());when(receipts.list(me,"id")).thenReturn(List.of(receipt));
   var domains=mock(AgentDomainVerifier.class);when(domains.verify(spec.get(2),receipt,spec.get(0),false)).thenReturn(true);
   assertTrue(new AgentOutcomeVerifier(store,receipts,computer,courses,chat,json,domains).verify(me,"id",0,"document",json.createObjectNode()).complete(),spec.get(0));
  }
 }
 @Test void clickNavigationStillRequiresTheActualDestination(){
  for(String verb:List.of("点击","点开","帮我打开")){
   var task=new AgentTaskStore.View("id",0,verb+"周课表","RUNNING",json.createObjectNode(),json.createObjectNode(),LocalDateTime.now(),LocalDateTime.now());when(store.check(me,"id",0,"document")).thenReturn(task);when(receipts.list(me,"id")).thenReturn(List.of());
   var v=new AgentOutcomeVerifier(store,receipts,computer,courses,chat,json,mock(AgentDomainVerifier.class));
   assertFalse(v.verify(me,"id",0,"document",json.valueToTree(Map.of("path","/dashboard"))).complete());
   assertTrue(v.verify(me,"id",0,"document",json.valueToTree(Map.of("path","/schedule-week"))).complete());
  }
 }
 final ObjectMapper json=new ObjectMapper();final AgentTaskStore store=mock(AgentTaskStore.class);final AgentMutationReceiptService receipts=mock(AgentMutationReceiptService.class);
 final ComputerAgentService computer=mock(ComputerAgentService.class);final CourseService courses=mock(CourseService.class);final ChatService chat=mock(ChatService.class);
 final ComputerAgentDtos.Session me=new ComputerAgentDtos.Session(1L,"SUPER_ADMIN",1L,"session","/dashboard",List.of("ASSISTANT_USE","COURSE_READ"),null);
 @Test void openingDashboardCannotCompleteOpeningTrainingPlans(){
  var task=new AgentTaskStore.View("id",0,"打开训练计划","RUNNING",json.createObjectNode(),json.createObjectNode(),LocalDateTime.now(),LocalDateTime.now());
  when(store.check(me,"id",0,"document")).thenReturn(task);when(receipts.list(me,"id")).thenReturn(List.of());
  var verifier=new AgentOutcomeVerifier(store,receipts,computer,courses,chat,json,mock(AgentDomainVerifier.class));
  var result=verifier.verify(me,"id",0,"document",json.valueToTree(Map.of("path","/dashboard","selectedTabs",List.of())));
  assertFalse(result.complete());assertEquals("UNSATISFIED",result.outcomes().get(0).status());
 }
 @Test void unrelatedMessageSuccessCannotCompleteCoursePriceMutation(){
  var task=new AgentTaskStore.View("id",0,"把流瑜伽价格改成99","RUNNING",json.createObjectNode(),json.createObjectNode(),LocalDateTime.now(),LocalDateTime.now());when(store.check(me,"id",0,"document")).thenReturn(task);
  when(receipts.list(me,"id")).thenReturn(List.of(new AgentMutationReceiptService.Receipt("r","id",0,"POST","/api/v1/messages/send","SUCCEEDED",json.valueToTree(Map.of("recipientUserId",2)),json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("id",99))),LocalDateTime.now().plusMinutes(1))));
  assertFalse(new AgentOutcomeVerifier(store,receipts,computer,courses,chat,json,mock(AgentDomainVerifier.class)).verify(me,"id",0,"document",json.createObjectNode()).complete());
 }
 @Test void unknownAdditionalRequirementCannotBeSilentlyDropped(){
  var task=new AgentTaskStore.View("id",0,"打开训练计划，帮我飞天","RUNNING",json.createObjectNode(),json.createObjectNode(),LocalDateTime.now(),LocalDateTime.now());when(store.check(me,"id",0,"document")).thenReturn(task);when(receipts.list(me,"id")).thenReturn(List.of());
  var report=new AgentOutcomeVerifier(store,receipts,computer,courses,chat,json,mock(AgentDomainVerifier.class)).verify(me,"id",0,"document",json.valueToTree(Map.of("path","/training-plans")));assertFalse(report.complete());assertTrue(report.partial());assertEquals(2,report.outcomes().size());
 }
 @Test void separateCapacityClauseInheritsTheUniqueCourseTarget(){
  var task=new AgentTaskStore.View("id",0,"把流瑜伽价格改成99，并把容量改成21，其他字段不变","RUNNING",json.createObjectNode(),json.createObjectNode(),LocalDateTime.now(),LocalDateTime.now());when(store.check(me,"id",0,"document")).thenReturn(task);
  var c=new com.gym.fitness.module.course.entity.CourseEntity();c.setId(1L);c.setName("流瑜伽");c.setFee(new java.math.BigDecimal("99"));c.setMaxCapacity(21);when(courses.listCourses()).thenReturn(List.of(c));
  when(receipts.list(me,"id")).thenReturn(List.of(new AgentMutationReceiptService.Receipt("r","id",0,"PUT","/api/v1/admin/courses/1","SUCCEEDED",json.createObjectNode(),json.createObjectNode(),json.valueToTree(Map.of("code",0,"data",Map.of("id",1,"fee",99,"maxCapacity",21))),LocalDateTime.now())));
  assertTrue(new AgentOutcomeVerifier(store,receipts,computer,courses,chat,json,mock(AgentDomainVerifier.class)).verify(me,"id",0,"document",json.createObjectNode()).complete());
 }
 @Test void staleOrWrongKeywordQueryCannotCompleteTheRequestedSearch(){
  for(var proof:List.of(Map.of("tool","leads","truncated",false,"asOf",LocalDateTime.now().minusDays(1).toString(),"arguments",Map.of("keyword","小周")),Map.of("tool","leads","truncated",false,"asOf",LocalDateTime.now().toString(),"arguments",Map.of("keyword","其他人")))){
   var checkpoint=json.createObjectNode();checkpoint.set("queryProofs",json.valueToTree(List.of(proof)));var task=new AgentTaskStore.View("id",0,"查询姓名为小周的潜客","RUNNING",json.createObjectNode(),checkpoint,LocalDateTime.now(),LocalDateTime.now());when(store.check(me,"id",0,"document")).thenReturn(task);when(receipts.list(me,"id")).thenReturn(List.of());
   assertFalse(new AgentOutcomeVerifier(store,receipts,computer,courses,chat,json,mock(AgentDomainVerifier.class)).verify(me,"id",0,"document",json.createObjectNode()).complete());
  }
 }
 @Test void aLaterDifferentQueryCannotEraseAnAlreadyVerifiedMatchingSearch(){
  var checkpoint=json.createObjectNode();checkpoint.set("queryProofs",json.valueToTree(List.of(Map.of("tool","leads","truncated",false,"asOf",LocalDateTime.now().toString(),"arguments",Map.of("keyword","小周")),Map.of("tool","leads","truncated",false,"asOf",LocalDateTime.now().toString(),"arguments",Map.of("keyword","小李")))));
  var task=new AgentTaskStore.View("id",0,"查询姓名为小周的潜客","RUNNING",json.createObjectNode(),checkpoint,LocalDateTime.now(),LocalDateTime.now());when(store.check(me,"id",0,"document")).thenReturn(task);when(receipts.list(me,"id")).thenReturn(List.of());
  assertTrue(new AgentOutcomeVerifier(store,receipts,computer,courses,chat,json,mock(AgentDomainVerifier.class)).verify(me,"id",0,"document",json.createObjectNode()).complete());
 }
}
