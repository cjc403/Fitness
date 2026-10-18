package com.gym.fitness.module.agent.task;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.config.AiProperties;
import com.gym.fitness.module.agent.computer.ComputerAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AgentTaskFeatureTest {
 @Test void disablingV2RejectsNewPlanningAndPermitsButKeepsManualRequestsAvailable()throws Exception{
  var config=new AiProperties();config.setTaskV2Enabled(false);var store=mock(AgentTaskStore.class);var computer=mock(ComputerAgentService.class);var receipts=mock(AgentMutationReceiptService.class);
  var controller=new AgentTaskController(store,mock(AgentTaskPlanner.class),computer,new ObjectMapper().findAndRegisterModules(),receipts,mock(AgentOutcomeVerifier.class),config);
  assertEquals(1,controller.capabilities().getData().get("version"));assertThrows(RuntimeException.class,()->controller.create("{}"));assertThrows(RuntimeException.class,()->controller.next("id","{}"));assertThrows(RuntimeException.class,()->controller.prepare("id","{}"));assertThrows(RuntimeException.class,()->controller.approve("id","permit","{}"));
  var filter=new AgentMutationFilter(receipts,computer,new ObjectMapper().findAndRegisterModules(),config);var request=new MockHttpServletRequest("PUT","/api/v1/admin/courses/1");request.addHeader("X-FitChain-Agent-Permit","old-permit");var response=new MockHttpServletResponse();var calls=new java.util.concurrent.atomic.AtomicInteger();
  filter.doFilter(request,response,(req,res)->calls.incrementAndGet());assertEquals(0,calls.get());assertEquals(400,response.getStatus());verifyNoInteractions(receipts);
  filter.doFilter(new MockHttpServletRequest("PUT","/api/v1/admin/courses/1"),new MockHttpServletResponse(),(req,res)->calls.incrementAndGet());assertEquals(1,calls.get());
 }
}
