package com.gym.fitness.module.agent.task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AgentGoalBindingTest {
 @Test void bindsExactContactAndRequestedContent(){assertTrue(AgentGoalBinding.messageMatches("给cjc2发送 你好啊", "cjc2", 2,"你好啊"));assertFalse(AgentGoalBinding.messageMatches("给cjc2发送 你好啊","cjc",3,"你好啊"));assertFalse(AgentGoalBinding.messageMatches("给cjc2发送 你好啊","cjc2",2,"别的内容"));}
 @Test void supportsQuotedContentAndExplicitAccountId(){assertTrue(AgentGoalBinding.messageMatches("给账号#2发送：‘周五见’","同名用户",2,"周五见"));}
 @Test void absentOrAmbiguousContentCannotPass(){assertFalse(AgentGoalBinding.messageMatches("给cjc2发消息","cjc2",2,"编造的内容"));}
 @Test void aChineseNameSubstringCannotSubstituteForTheRequestedCourse(){assertFalse(AgentGoalBinding.labelMatches("把流瑜伽价格改成99","瑜伽",1));assertTrue(AgentGoalBinding.labelMatches("只把流瑜伽的价格改成99","流瑜伽",2));}
 @Test void conversationalMessageWordingKeepsRecipientAndContentTogether(){assertTrue(AgentGoalBinding.messageMatches("帮我发个消息给系统管理员，你好","系统管理员",1,"你好"));assertTrue(AgentGoalBinding.messageMatches("发给系统管理员：你好","系统管理员",1,"你好"));}
}
