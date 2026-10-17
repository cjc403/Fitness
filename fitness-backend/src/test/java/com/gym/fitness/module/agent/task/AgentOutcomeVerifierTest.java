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
}
