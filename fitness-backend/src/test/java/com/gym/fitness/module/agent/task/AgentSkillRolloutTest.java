package com.gym.fitness.module.agent.task;
import com.gym.fitness.config.AiProperties;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AgentSkillRolloutTest {
 @Test void eightGroupsDefaultOnAndCanBeDisabledIndependently(){
  var p=new AiProperties();var r=new AgentSkillRollout(p);assertEquals(8,r.enabled().size());
  p.setTaskDisabledSkills(Set.of("courses","messages"));assertEquals(6,r.enabled().size());
  assertFalse(r.queryEnabled("courses"));assertTrue(r.queryEnabled("members"));
  assertThrows(RuntimeException.class,()->r.requireMutation("/api/v1/admin/courses/1"));
  assertThrows(RuntimeException.class,()->r.requireMutation("/api/v1/messages/send"));
  assertDoesNotThrow(()->r.requireMutation("/api/v1/admin/bookings"));
 }
 @Test void navigationAndUnknownConfigurationFailClosed(){
  var p=new AiProperties();var r=new AgentSkillRollout(p);p.setTaskDisabledSkills(Set.of("navigation"));
  assertThrows(RuntimeException.class,r::requireNavigation);
  p.setTaskDisabledSkills(Set.of("typo"));assertThrows(RuntimeException.class,r::enabled);
 }
}
