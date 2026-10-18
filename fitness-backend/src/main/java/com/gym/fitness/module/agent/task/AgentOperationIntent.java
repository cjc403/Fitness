package com.gym.fitness.module.agent.task;
import java.util.*;
import java.util.regex.Pattern;

/** Operation intent only; identity, exact objects, payload and approval are checked separately. */
final class AgentOperationIntent {
 private AgentOperationIntent(){}
 static String unquoted(String source){var text=new StringBuilder();char end=0;for(char c:source.toCharArray()){if(end!=0){if(c==end)end=0;text.append(' ');}else if("‘“\"「".indexOf(c)>=0){end=switch(c){case '‘'->'’';case '“'->'”';case '「'->'」';default->c;};text.append(' ');}else text.append(c);}return text.toString();}
 static List<String> positiveClauses(String goal){
  var result=new ArrayList<String>();for(var clause:AgentGoalClauses.parse(goal)){
   String text=unquoted(clause);var message=Pattern.compile("发送|发(?:个|一条)?消息|发给").matcher(text);if(message.find())text=text.substring(0,message.end());
   text=text.replaceAll("(?:不要|不必|无需|别|不)(?:再|去)?(?:提交|保存|发送|发布|新建|创建|新增|预约|取消|删除|登记|作废|归档)[^，,。；;]*","").trim();
   if(text.matches("^(?:请|帮我|麻烦|先|只|仅|我想)*(?:查询|查看|统计|浏览|告诉我|打开|进入|切换到|点击|点开).*$")){
    var next=Pattern.compile("(?:后|并且|并|再|然后)(?=(?:给|为|把|将|新建|新增|创建|修改|更新|调整|发送|发布|预约|取消|删除|登记|作废|归档))").matcher(text);
    if(!next.find())continue;text=text.substring(next.end());
   }
   if(!text.isBlank())result.add(text);
  }return result;
 }
 static boolean permits(String goal,String method,String path){
  if(!registeredMethod(method,path))return false;
  if(path.equals("/api/v1/admin/courses")&&method.equals("POST")&&positiveClauses(goal).stream().noneMatch(c->c.matches(".*(?:课程|团课|团体课|私教课|常规课).*")))return false;
  String outside=unquoted(goal);
  if(outside.matches("(?s).*(?:^|[，,。；;]|但|但是|并且)(?:不要|不必|无需|别|不)(?:再)?(?:提交|保存).*"))return false;
  String operation;
  if(path.equals("/api/v1/messages/send")&&method.equals("POST")){if(outside.matches("(?s).*(?:^|[，,。；;]|但|但是|并且)(?:不要|不必|无需|别|不)(?:再)?发送.*"))return false;operation="发送|发(?:个|一条)?消息|发给";}
  else if(path.matches("/api/v1/admin/courses(?:/\\d+)?"))operation=method.equals("DELETE")?"删除|移除|删掉":method.equals("POST")?"新建|新增|创建|添加":method.equals("PUT")?"修改|更新|编辑|调整|改成|改为|改到|设为|设成":"(?!)";
  else if(path.matches("/api/v1/admin/schedules(?:/\\d+)?"))operation=method.equals("DELETE")?"删除|移除|删掉":method.equals("POST")?"创建|新增|安排|排课|排一":method.equals("PUT")?"修改|更新|调整|改成|改为|改到":"(?!)";
  else if(path.matches("/api/v1/(?:admin|member)/bookings(?:/\\d+/(?:cancel|check-in|no-show|leave))?"))operation=path.endsWith("/cancel")?"取消":path.endsWith("/check-in")?"签到":path.endsWith("/no-show")?"爽约|未到|未出席":path.endsWith("/leave")?"请假":"(?<!取消)预约";
  else if(path.matches("/api/v1/(?:member/)?training-plans(?:/\\d+(?:/(?:draft|publish|archive|sessions)|/sessions/\\d+/(?:feedback|void)))?"))operation=path.endsWith("/publish")?"发布":path.endsWith("/archive")?"归档":path.endsWith("/feedback")?"反馈":path.endsWith("/void")?"作废":path.endsWith("/sessions")?"登记|录入|记录":path.endsWith("/draft")?"修改|编辑|保存|更新":"创建|新建|新增|保存草稿";
  else if(path.matches("/api/v1/leads(?:/\\d+(?:/(?:profile|follow-ups|trials)(?:/\\d+/result)?)?)?"))operation=path.endsWith("/profile")?"修改|更新|编辑":path.endsWith("/follow-ups")?"跟进|回访|联系":path.endsWith("/result")?"记录为|登记|标记|改为|改成|更新":path.endsWith("/trials")?"安排|预约|登记":path.matches("/api/v1/leads/?")?"新建|新增|创建|添加":"(?!)";
  else if(path.matches("/api/v1/admin/members(?:/\\d+(?:/follow-ups)?)?"))operation=path.endsWith("/follow-ups")?"登记|新增|添加|回访|跟进":method.equals("POST")?"新建|新增|创建|登记":method.equals("PUT")?"修改|更新|编辑|调整|改为|改成":"(?!)";
  else return false;
  return positiveClauses(goal).stream().anyMatch(c->Pattern.compile(operation).matcher(c).find());
 }
 private static boolean registeredMethod(String method,String path){
  if(path.equals("/api/v1/messages/send"))return method.equals("POST");
  if(path.matches("/api/v1/admin/(?:courses|schedules)"))return method.equals("POST");
  if(path.matches("/api/v1/admin/(?:courses|schedules)/\\d+"))return Set.of("PUT","DELETE").contains(method);
  if(path.matches("/api/v1/(?:admin|member)/bookings/\\d+/cancel"))return method.equals("PUT");
  if(path.matches("/api/v1/(?:admin|member)/bookings(?:/\\d+/(?:check-in|no-show|leave))?"))return method.equals("POST");
  if(path.startsWith("/api/v1/leads")||path.matches("/api/v1/(?:member/)?training-plans.*"))return method.equals("POST");
  if(path.matches("/api/v1/admin/members/\\d+"))return method.equals("PUT");
  if(path.equals("/api/v1/admin/members")||path.matches("/api/v1/admin/members/\\d+/follow-ups"))return method.equals("POST");
  return false;
 }
}
