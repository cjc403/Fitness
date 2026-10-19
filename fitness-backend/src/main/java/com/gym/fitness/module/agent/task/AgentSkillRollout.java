package com.gym.fitness.module.agent.task;

import com.gym.fitness.config.AiProperties;
import java.util.*;
import static com.gym.fitness.module.agent.AgentIdentity.invalid;

/** Additional V2 rollout restrictions; never grants a permission or changes manual business APIs. */
public final class AgentSkillRollout {
 private static final Set<String> GROUPS=Set.of("navigation","messages","courses","members","leads","retention","training","operations");
 private final AiProperties config;
 public AgentSkillRollout(AiProperties config){this.config=config;}
 public Set<String> enabled(){var disabled=config.getTaskDisabledSkills();if(disabled==null||!GROUPS.containsAll(disabled))throw invalid("Agent技能开关配置无效，请联系管理员核对");var enabled=new TreeSet<>(GROUPS);enabled.removeAll(disabled);return Collections.unmodifiableSet(enabled);}
 public boolean queryEnabled(String name){String group=switch(name){case "courses"->"courses";case "members","bookings"->"members";case "leads","lead_options"->"leads";case "retention"->"retention";case "training_plans","students"->"training";case "analytics","cash_summary"->"operations";case "contacts","messages"->"messages";default->null;};return group==null||enabled().contains(group);}
 public void requireNavigation(){require("navigation");}
 public void requireMutation(String path){
  String group=path.contains("/training-plans")?"training":path.contains("/follow-ups")&&path.contains("/members/")?"retention":path.contains("/leads")?"leads":path.contains("/messages/")?"messages":path.contains("/courses")||path.contains("/schedules")||path.contains("/admin/rooms")?"courses":path.contains("/members")||path.contains("/bookings")?"members":"operations";
  require(group);
 }
 private void require(String group){if(!enabled().contains(group))throw invalid("该组Agent能力暂未启用（"+group+"），请使用原业务页面手动办理；没有执行本次操作");}
}
