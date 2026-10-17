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
        when(model.completePlanning(anyList(),anyList(),anyBoolean())).thenReturn(action("navigate","{\"path\":\"/training-plans\"}","opaque internal context"));
        var first=planner.next(task.id(),request());assertEquals("navigate",first.decision().action());
        assertFalse(json.writeValueAsString(first).contains("opaque internal context"));
        when(model.completePlanning(anyList(),anyList(),anyBoolean())).thenAnswer(i->{List<Map<String,Object>> messages=i.getArgument(0);assertTrue(messages.stream().anyMatch(m->"opaque internal context".equals(m.get("reasoning_content"))));assertTrue(messages.stream().anyMatch(m->"tool".equals(m.get("role"))&&"provider-id".equals(m.get("tool_call_id"))));return action("finish","{}","new context");});
        var second=(com.fasterxml.jackson.databind.node.ObjectNode)request();second.put("version",1);planner.next(task.id(),second);
    }
    @Test void staleLeaseIsRejectedBeforeCallingModel(){
        when(store.check(eq(me),anyString(),anyLong(),anyString())).thenThrow(new IllegalArgumentException("stale lease"));
        assertThrows(RuntimeException.class,()->planner.next(task.id(),request()));verifyNoInteractions(model);
    }
    @Test void providerFailureReturnsTypedMetadataWithoutInventingAction(){
        when(model.completePlanning(anyList(),anyList(),anyBoolean())).thenReturn(new AgentModelClient.Outcome(null,new AgentModelClient.Failure("RATE_LIMITED",true,"trace-test")));
        var turn=planner.next(task.id(),request());assertNull(turn.decision());assertEquals("RATE_LIMITED",turn.failure().code());assertEquals("RATE_LIMITED",turn.task().checkpoint().path("lastFailure").path("code").asText());assertEquals(1,turn.task().checkpoint().path("modelCalls").asInt());
    }
    @Test void parallelToolActionsAreNeverDispatched()throws Exception{
        var one=action("navigate","{}","").completion();
        when(model.completePlanning(anyList(),anyList(),anyBoolean())).thenReturn(new AgentModelClient.Outcome(new AgentModelClient.Completion("",List.of(one.calls().get(0),one.calls().get(0)),"fixture",1,one.providerTurn()),null));
        assertThrows(RuntimeException.class,()->planner.next(task.id(),request()));
    }
    @Test void proseOnlyTurnGetsOneBoundedCorrectionInsteadOfFailingTheTask()throws Exception{
        var prose=new AgentModelClient.Completion("我将打开页面",List.of(),"fixture",12,Map.of("role","assistant","content","我将打开页面","reasoning_content","opaque"));
        when(model.completePlanning(anyList(),anyList(),anyBoolean())).thenReturn(new AgentModelClient.Outcome(prose,null),action("navigate","{\"path\":\"/training-plans\"}","fixed"));
        assertEquals("navigate",planner.next(task.id(),request()).decision().action());
        verify(model,times(2)).completePlanning(anyList(),anyList(),eq(false));
    }
    @Test void persistedBudgetCannotBeResetByForgettingTheInMemoryEpisode(){
        var cp=json.createObjectNode().put("modelCalls",100).put("totalTokens",1000);
        task=new AgentTaskStore.View(task.id(),0,task.goal(),task.status(),task.plan(),cp,task.createdAt(),task.updatedAt());
        when(store.check(eq(me),anyString(),anyLong(),anyString())).thenReturn(task);
        assertThrows(RuntimeException.class,()->planner.next(task.id(),request()));verifyNoInteractions(model);
    }
    @Test void boundedSemanticFormIsOneExplicitNonSubmittingAction()throws Exception{
        when(model.completePlanning(anyList(),anyList(),anyBoolean())).thenReturn(action("use_skill","{\"kind\":\"fill_fields\",\"fields\":[{\"field\":\"title\",\"value\":\"基础训练\"},{\"field\":\"exercises.0.name\",\"value\":\"深蹲\"}]}","opaque"));
        assertEquals("use_skill",planner.next(task.id(),request()).decision().action());
    }
}
