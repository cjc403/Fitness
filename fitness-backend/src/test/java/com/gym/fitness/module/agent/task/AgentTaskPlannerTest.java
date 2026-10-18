package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.agent.*;
import com.gym.fitness.module.agent.computer.*;
import com.gym.fitness.module.assistant.service.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentTaskPlannerTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final AgentTaskStore store=mock(AgentTaskStore.class);
    final ComputerAgentService computer=mock(ComputerAgentService.class);
    final AgentModelClient model=mock(AgentModelClient.class);
    final AgentBusiness business=mock(AgentBusiness.class);
    final AgentTools tools=mock(AgentTools.class);
    final AgentMutationReceiptService receipts=mock(AgentMutationReceiptService.class);
    final ComputerAgentDtos.Session me=new ComputerAgentDtos.Session(1L,"COACH",1L,"session","/coach-workbench",List.of("ASSISTANT_USE"),null);
    AgentTaskPlanner planner;AgentTaskStore.View task;
    @BeforeEach void setup(){
        task=new AgentTaskStore.View("12345678-1234-1234-1234-123456789abc",0,"打开训练计划", "RUNNING",json.createObjectNode(),json.createObjectNode(),LocalDateTime.now(),LocalDateTime.now());
        when(computer.session()).thenReturn(me);when(store.check(eq(me),anyString(),anyLong(),anyString())).thenReturn(task);
        when(store.update(eq(me),anyString(),anyLong(),anyString(),anyString(),any(),any())).thenAnswer(i->new AgentTaskStore.View(task.id(),1,task.goal(),i.getArgument(4),json.valueToTree(i.getArgument(5)),json.valueToTree(i.getArgument(6)),task.createdAt(),LocalDateTime.now()));
        when(computer.planningFrame(any())).thenReturn(new ComputerAgentService.PlanningFrame("system role rules",List.of()));
        when(tools.available()).thenReturn(List.of());
        when(computer.validatePlannedAction(anyString(),any(),any())).thenAnswer(i->new ComputerAgentDtos.Decision(i.getArgument(0),i.getArgument(1),"validated"));
        when(receipts.list(any(),anyString())).thenReturn(List.of());
        var counter=new java.util.concurrent.atomic.AtomicInteger();when(store.reserveModelCall(any(),anyString(),anyLong(),anyString())).thenAnswer(i->counter.incrementAndGet());
        planner=new AgentTaskPlanner(store,computer,model,business,tools,json,receipts);
    }
    JsonNode request(){return json.valueToTree(Map.of("version",0,"document","document-a","observation",Map.of("url","http://localhost:5173/coach-workbench","title","教练","text","当前页","revision","r1","nodes",List.of()),"history",List.of(),"artifacts",List.of()));}
    AgentModelClient.Outcome action(String name,String args,String reasoning)throws Exception{
        var call=new AgentModelClient.Call("provider-id",name,args);var turn=Map.<String,Object>of("role","assistant","content","","reasoning_content",reasoning,"tool_calls",List.of(Map.of("id",call.id(),"type","function","function",Map.of("name",name,"arguments",args))));
        return new AgentModelClient.Outcome(new AgentModelClient.Completion("",List.of(call),"fixture",1,turn),null);
    }
    @Test void genuineProviderHistoryIsRetainedAndNeverReturnedToBrowser()throws Exception{
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenReturn(action("navigate","{\"path\":\"/training-plans\"}","opaque internal context"));
        var first=planner.next(task.id(),request());assertEquals("navigate",first.decision().action());
        assertFalse(json.writeValueAsString(first).contains("opaque internal context"));
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenAnswer(i->{List<Map<String,Object>> messages=i.getArgument(0);assertTrue(messages.stream().anyMatch(m->"opaque internal context".equals(m.get("reasoning_content"))));assertTrue(messages.stream().anyMatch(m->"tool".equals(m.get("role"))&&"provider-id".equals(m.get("tool_call_id"))));return action("finish","{}","new context");});
        var second=(com.fasterxml.jackson.databind.node.ObjectNode)request();second.put("version",1);planner.next(task.id(),second);
    }
    @Test void aServerVerificationVersionKeepsTheGenuineQueryResultAvailable()throws Exception{
        when(tools.available()).thenReturn(List.of(new AgentDtos.Tool("students","学员","真实关系",false,json.createObjectNode())));
        when(business.query(eq("students"),any())).thenReturn(new AgentDtos.ToolResult("students","学员",json.valueToTree(List.of(Map.of("id",17,"name","宁安"))),"db",LocalDateTime.now(),false));
        when(business.modelContext(any())).thenReturn(Map.of("tool","students","data",List.of(Map.of("id",17,"name","宁安"))));
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenReturn(action("query_students","{}","genuine student reasoning"));
        planner.next(task.id(),request());planner.acknowledgeVerification(task.id(),1,2);
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenAnswer(i->{String messages=i.getArgument(0).toString();assertTrue(messages.contains("genuine student reasoning"));assertTrue(messages.contains("宁安"));assertTrue(messages.contains("17"));return action("navigate","{\"path\":\"/training-plans\"}","next");});
        var r=(com.fasterxml.jackson.databind.node.ObjectNode)request();r.put("version",2);assertEquals("navigate",planner.next(task.id(),r).decision().action());
    }
    @Test void nativeConfirmationMustLeadToTheFrozenRequestPreviewInsteadOfAnExtraHumanQuestion()throws Exception{
        task=new AgentTaskStore.View(task.id(),0,"帮我预约流瑜伽","RUNNING",task.plan(),task.checkpoint(),task.createdAt(),task.updatedAt());when(store.check(eq(me),anyString(),anyLong(),anyString())).thenReturn(task);
        var r=(com.fasterxml.jackson.databind.node.ObjectNode)request();((com.fasterxml.jackson.databind.node.ObjectNode)r.get("observation")).set("context",json.valueToTree(Map.of("scope",Map.of("nativeConfirmation",true))));
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenReturn(action("ask_user","{\"question\":\"需要您本人确认预约，请回复确认预约\"}","first"),action("click","{\"ref\":\"confirm-native\"}","repaired"));
        assertEquals("click",planner.next(task.id(),r).decision().action());verify(model,times(2)).completePlanning(anyList(),anyList(),anyBoolean(),any());
    }
    @Test void staleLeaseIsRejectedBeforeCallingModel(){
        when(store.check(eq(me),anyString(),anyLong(),anyString())).thenThrow(new IllegalArgumentException("stale lease"));
        assertThrows(RuntimeException.class,()->planner.next(task.id(),request()));verifyNoInteractions(model);
    }
    @Test void aShortBusinessWriteStillUsesPlanningThinking()throws Exception{
        task=new AgentTaskStore.View(task.id(),0,"取消我的预约编号#29","RUNNING",task.plan(),task.checkpoint(),task.createdAt(),task.updatedAt());
        when(store.check(eq(me),anyString(),anyLong(),anyString())).thenReturn(task);
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenAnswer(i->{assertTrue((Boolean)i.getArgument(2));return action("observe","{}","opaque");});
        planner.next(task.id(),request());
    }
    @Test void earlierPageSnapshotsDoNotAccumulateBesideTheCurrentObservation()throws Exception{
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenReturn(action("observe","{}","opaque"));
        var first=(com.fasterxml.jackson.databind.node.ObjectNode)request();((com.fasterxml.jackson.databind.node.ObjectNode)first.get("observation")).put("text","old observation sentinel");planner.next(task.id(),first);
        var second=(com.fasterxml.jackson.databind.node.ObjectNode)request();second.put("version",1);((com.fasterxml.jackson.databind.node.ObjectNode)second.get("observation")).put("text","current observation sentinel");
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenAnswer(i->{assertFalse(i.getArgument(0).toString().contains("old observation sentinel"));assertTrue(i.getArgument(0).toString().contains("current observation sentinel"));assertTrue(((java.time.Duration)i.getArgument(3)).toMillis()<=90000);return action("finish","{}","new");});
        planner.next(task.id(),second);
    }
    @Test void providerFailureReturnsTypedMetadataWithoutInventingAction(){
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenReturn(new AgentModelClient.Outcome(null,new AgentModelClient.Failure("RATE_LIMITED",true,"trace-test")));
        var turn=planner.next(task.id(),request());assertNull(turn.decision());assertEquals("RATE_LIMITED",turn.failure().code());assertEquals("RATE_LIMITED",turn.task().checkpoint().path("lastFailure").path("code").asText());assertEquals(1,turn.task().checkpoint().path("modelCalls").asInt());
    }
    @Test void parallelToolActionsAreNeverDispatched()throws Exception{
        var one=action("navigate","{}","").completion();
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenReturn(new AgentModelClient.Outcome(new AgentModelClient.Completion("",List.of(one.calls().get(0),one.calls().get(0)),"fixture",1,one.providerTurn()),null));
        assertThrows(RuntimeException.class,()->planner.next(task.id(),request()));
    }
    @Test void proseOnlyTurnGetsOneBoundedCorrectionInsteadOfFailingTheTask()throws Exception{
        var prose=new AgentModelClient.Completion("我将打开页面",List.of(),"fixture",12,Map.of("role","assistant","content","我将打开页面","reasoning_content","opaque"));
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenReturn(new AgentModelClient.Outcome(prose,null),action("navigate","{\"path\":\"/training-plans\"}","fixed"));
        assertEquals("navigate",planner.next(task.id(),request()).decision().action());
        verify(model,times(2)).completePlanning(anyList(),anyList(),eq(false),any());
    }
    @Test void persistedBudgetCannotBeResetByForgettingTheInMemoryEpisode(){
        var cp=json.createObjectNode().put("modelCalls",100).put("totalTokens",1000);
        task=new AgentTaskStore.View(task.id(),0,task.goal(),task.status(),task.plan(),cp,task.createdAt(),task.updatedAt());
        when(store.check(eq(me),anyString(),anyLong(),anyString())).thenReturn(task);
        assertThrows(RuntimeException.class,()->planner.next(task.id(),request()));verifyNoInteractions(model);
    }
    @Test void boundedSemanticFormIsOneExplicitNonSubmittingAction()throws Exception{
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenReturn(action("use_skill","{\"kind\":\"fill_fields\",\"fields\":[{\"field\":\"title\",\"value\":\"基础训练\"},{\"field\":\"exercises.0.name\",\"value\":\"深蹲\"}]}","opaque"));
        assertEquals("use_skill",planner.next(task.id(),request()).decision().action());
    }
    @Test void aRejectedPageActionGetsAGenuineToolErrorBeforeTheModelRepairsIt()throws Exception{
        when(model.completePlanning(anyList(),anyList(),anyBoolean(),any())).thenReturn(action("click","{\"ref\":\"old-ref\"}","original"),action("navigate","{\"path\":\"/training-plans\"}","repaired"));
        when(computer.validatePlannedAction(eq("click"),any(),any())).thenThrow(com.gym.fitness.module.agent.AgentIdentity.invalid("节点引用已经过期"));
        assertEquals("navigate",planner.next(task.id(),request()).decision().action());
        var calls=org.mockito.ArgumentCaptor.forClass(List.class);verify(model,times(2)).completePlanning(calls.capture(),anyList(),anyBoolean(),any());assertTrue(calls.getAllValues().get(1).stream().anyMatch(v->v.toString().contains("INVALID_ACTION")));
    }
}
