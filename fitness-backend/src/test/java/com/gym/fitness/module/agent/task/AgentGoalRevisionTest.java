package com.gym.fitness.module.agent.task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AgentGoalRevisionTest {
 @Test void newPriceReplacesOldRequirementAndKeepsUnchangedCapacity(){String goal=AgentGoalRevision.apply("把流瑜伽价格改成99，容量保持不变","价格改成109");assertTrue(goal.contains("109"));assertFalse(goal.contains("99"));assertTrue(goal.contains("容量保持不变"));}
 @Test void twoPriceTargetsRequireExplicitDisambiguation(){assertThrows(RuntimeException.class,()->AgentGoalRevision.apply("把课程A价格改成99，把课程B价格改成88","价格改成109"));}
 @Test void anAdditionalGoalDoesNotErasePreviouslyRequestedWork(){String goal=AgentGoalRevision.apply("打开训练计划","再查询本周课程");assertTrue(goal.contains("打开训练计划"));assertTrue(goal.contains("查询本周课程"));}
}
