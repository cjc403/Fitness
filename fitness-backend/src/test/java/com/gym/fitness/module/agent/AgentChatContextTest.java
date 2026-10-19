package com.gym.fitness.module.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentChatContextTest {
 private final ObjectMapper json=new ObjectMapper();
 private final AgentChatContext.Owner owner=new AgentChatContext.Owner(1L,"MANAGER",2L,"session-a");
 @Test void expiredConditionsCannotBeRecoveredWithAnOldContextId(){
  Clock clock=mock(Clock.class);Instant start=Instant.parse("2026-10-19T00:00:00Z");when(clock.instant()).thenReturn(start);
  var store=new AgentChatContext(clock);var c=store.open(null,owner);store.remember(c,"analytics",json.createObjectNode().put("from","2026-10-01"),null);
  when(clock.instant()).thenReturn(start.plusSeconds(901));var fresh=store.open(c.id(),owner);assertThat(fresh.id()).isNotEqualTo(c.id());assertThat(fresh.queries()).isEmpty();
 }
 @Test void returnedQueryConditionsAreDefensiveCopies(){
  var store=new AgentChatContext();var c=store.open(null,owner);var args=json.createObjectNode().put("keyword","原始对象");store.remember(c,"members",args,null);args.put("keyword","被改写");
  var read=store.open(c.id(),owner);((com.fasterxml.jackson.databind.node.ObjectNode)read.queries().get(0).arguments()).put("keyword","客户端改写");
  assertThat(store.open(c.id(),owner).queries().get(0).arguments().path("keyword").asText()).isEqualTo("原始对象");
 }
 @Test void boundedStoreEvictsOldContextsInsteadOfGrowingWithoutLimit(){var store=new AgentChatContext();var first=store.open(null,owner);store.remember(first,null,null,"analytics");for(int i=0;i<1000;i++)store.remember(store.open(null,owner),null,null,"analytics");assertThat(store.open(first.id(),owner).id()).isNotEqualTo(first.id());}
}
