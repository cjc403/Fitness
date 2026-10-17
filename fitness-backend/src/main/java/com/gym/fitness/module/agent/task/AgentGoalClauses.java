package com.gym.fitness.module.agent.task;

import java.util.*;

/** Split requested operations, retaining form details and quoted content with their operation. */
final class AgentGoalClauses {
 private AgentGoalClauses(){}
 static boolean fieldOnly(String clause){return clause.matches("^(?:(?:并且|并|再|且|把|将|只)\\s*)*(?:价格|费用|售价|容量|时长|强度|类型|简介|名称|所属).*" );}
 static String courseTarget(String goal,String name,long id){
  var target=new StringBuilder();boolean inherited=false;
  for(var clause:parse(goal)){boolean own=AgentGoalBinding.labelMatches(clause,name,id);if(own||(inherited&&fieldOnly(clause))){target.append(clause).append('，');inherited=true;}else inherited=false;}
  return target.toString();
 }
 static List<String> parse(String goal){
  var parts=new ArrayList<String>();var current=new StringBuilder();char closing=0;
  for(char c:goal.toCharArray()){
   if(closing!=0){current.append(c);if(c==closing)closing=0;continue;}
   if("‘“\"「".indexOf(c)>=0){closing=switch(c){case '‘'->'’';case '“'->'”';case '「'->'」';default->c;};current.append(c);continue;}
   if("，,。；;\n".indexOf(c)>=0){if(!current.toString().isBlank())parts.add(current.toString().trim());current.setLength(0);}else current.append(c);
  }
  if(!current.toString().isBlank())parts.add(current.toString().trim());
  var result=new ArrayList<String>();
  for(String raw:parts){
   for(String piece:raw.split("然后|再(?=告诉|查询|查看|发送|创建|安排|打开|给)")){
    String part=piece.trim();if(part.isBlank())continue;
    boolean detail=(part.matches("^(?:姓名|电话|手机|联系电话|咨询来源|来源|意向|自然到店|转介绍|高意向|中意向|低意向|其他默认|其他字段|标题|计划标题|评分|内容|备注|结果|接待顾问|训练目标|综合体能|减脂|增肌|深蹲|自重|时长|每组|每次|不发布|不保存|不提交|不要|保持|等待|确认后|保存后|核对成功后).*" )||part.matches("^\\d{4}-\\d{2}-\\d{2}(?:到|至|~).*"))&&!part.matches(".*(?:改为|改成|调整|设为).*" );
    boolean messageContinuation=!result.isEmpty()&&result.get(result.size()-1).matches(".*(?:发送|发(?:个|一条)?消息|发给).*" )&&!part.matches(".*(?:查询|查看|打开|创建|新增|发送|发(?:个|一条)?消息|发给|帮我|修改|改成|安排).*" );
    if(!result.isEmpty()&&(detail||messageContinuation))result.set(result.size()-1,result.get(result.size()-1)+"，"+part);else result.add(part);
   }
  }
  return List.copyOf(result);
 }
}
