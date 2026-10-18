package com.gym.fitness.module.agent.task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AgentGoalRevisionTest {
 @Test void priceCorrectionRetainsNoSubmitConstraint(){String goal=AgentGoalRevision.apply("把课程编号#1价格改成99","价格改成109，不要提交");assertTrue(goal.contains("109"));assertTrue(goal.contains("不要提交"));assertFalse(AgentOperationIntent.permits(goal,"PUT","/api/v1/admin/courses/1"));}
 @Test void objectSwitchCannotSilentlyApplyTheNewPriceToTheOldCourse(){assertThrows(RuntimeException.class,()->AgentGoalRevision.apply("把课程编号#1价格改成99","换成课程编号#2，价格改成109"));assertEquals("把课程编号#2价格改成109，不要提交",AgentGoalRevision.apply("把课程编号#1价格改成99","改为任务：把课程编号#2价格改成109，不要提交"));}
 @Test void newPriceReplacesOldRequirementAndKeepsUnchangedCapacity(){String goal=AgentGoalRevision.apply("把流瑜伽价格改成99，容量保持不变","价格改成109");assertTrue(goal.contains("109"));assertFalse(goal.contains("99"));assertTrue(goal.contains("容量保持不变"));}
 @Test void twoPriceTargetsRequireExplicitDisambiguation(){assertThrows(RuntimeException.class,()->AgentGoalRevision.apply("把课程A价格改成99，把课程B价格改成88","价格改成109"));}
 @Test void anAdditionalGoalDoesNotErasePreviouslyRequestedWork(){String goal=AgentGoalRevision.apply("打开训练计划","再查询本周课程");assertTrue(goal.contains("打开训练计划"));assertTrue(goal.contains("查询本周课程"));}
}
