package com.gym.fitness.module.agent.task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AgentOperationIntentTest {
 @Test void negativeInstructionsAndMessageContentsCannotAuthorizeOtherBusinessWrites(){
  assertFalse(AgentOperationIntent.permits("不要预约流瑜伽，只查询课程","POST","/api/v1/member/bookings"));
  assertFalse(AgentOperationIntent.permits("不要取消预约，只查看详情","PUT","/api/v1/admin/bookings/7/cancel"));
  assertFalse(AgentOperationIntent.permits("不要新建潜客，只查看潜客","POST","/api/v1/leads"));
  assertFalse(AgentOperationIntent.permits("给管理员发送：‘预约流瑜伽’","POST","/api/v1/member/bookings"));
  assertTrue(AgentOperationIntent.permits("给管理员发送：‘不要提交，不发送消息’","POST","/api/v1/messages/send"));
  assertFalse(AgentOperationIntent.permits("创建训练计划草稿，不发布","POST","/api/v1/training-plans/2/publish"));
 }
 @Test void registeredOperationsHaveSpecificPositiveIntent(){
  assertTrue(AgentOperationIntent.permits("把流瑜伽价格改成99","PUT","/api/v1/admin/courses/2"));
  assertTrue(AgentOperationIntent.permits("为会员张三预约流瑜伽","POST","/api/v1/admin/bookings"));
  assertTrue(AgentOperationIntent.permits("为计划#2登记今天训练","POST","/api/v1/training-plans/2/sessions"));
  assertTrue(AgentOperationIntent.permits("发布训练计划#2","POST","/api/v1/training-plans/2/publish"));
  assertTrue(AgentOperationIntent.permits("为潜客小李安排体验","POST","/api/v1/leads/2/trials"));
  assertFalse(AgentOperationIntent.permits("帮我现金退款999元","POST","/api/v1/orders/1/refund"));
 }
 @Test void methodAndResourceTypeCannotBeSubstitutedByAGenericCreateVerb(){
  assertFalse(AgentOperationIntent.permits("新建潜客小李","DELETE","/api/v1/leads"));
  assertFalse(AgentOperationIntent.permits("给训练计划#2提交反馈","PUT","/api/v1/member/training-plans/2/sessions/3/feedback"));
  assertFalse(AgentOperationIntent.permits("新建会员张三","POST","/api/v1/admin/courses"));
  assertTrue(AgentOperationIntent.permits("新建课程深蹲入门","POST","/api/v1/admin/courses"));
 }
 @Test void readingAnOperationNameDoesNotRequestTheOperation(){
  assertFalse(AgentOperationIntent.permits("查询我的预约","POST","/api/v1/member/bookings"));
  assertFalse(AgentOperationIntent.permits("查看已发布的训练计划","POST","/api/v1/training-plans/2/publish"));
  assertFalse(AgentOperationIntent.permits("点击预约与签到","POST","/api/v1/admin/bookings/2/check-in"));
  assertTrue(AgentOperationIntent.permits("查看会员后再给管理员发送：你好","POST","/api/v1/messages/send"));
 }
}
