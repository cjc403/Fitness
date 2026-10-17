package com.gym.fitness.module.agent.task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AgentGoalClausesTest {
 @Test void creationDetailsRemainPartOfTheSameGoal(){var c=AgentGoalClauses.parse("新建潜客，姓名小周，电话{{USER_DATA_1}}，自然到店，高意向，其他默认");assertEquals(1,c.size());assertTrue(c.get(0).contains("姓名小周"));}
 @Test void quotedMessageAndASeparateQueryAreDifferentGoals(){var c=AgentGoalClauses.parse("给小周发送：‘你好，周五见’，再查询本周课程");assertEquals(2,c.size());assertTrue(c.get(0).contains("你好，周五见"));}
 @Test void twoFieldChangesAndAnUnknownRequestAreNotDropped(){assertEquals(2,AgentGoalClauses.parse("把流瑜伽价格改成99，并把容量改成21").size());assertEquals(2,AgentGoalClauses.parse("打开训练计划，帮我飞天").size());}
}
