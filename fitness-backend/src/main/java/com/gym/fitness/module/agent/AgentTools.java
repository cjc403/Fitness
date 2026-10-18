package com.gym.fitness.module.agent;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.gym.fitness.common.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.gym.fitness.module.agent.AgentIdentity.*;
import static com.gym.fitness.module.agent.AgentDtos.*;

/** The server, rather than the model or browser, owns the tool and parameter allowlists. */
@Component @RequiredArgsConstructor
public class AgentTools {
 private final ObjectMapper json;
 private final AgentIdentity identity;
 private static final Set<String> ALL=Set.of("SUPER_ADMIN","MANAGER","RECEPTION","SALES","COACH","MEMBER");
 private boolean role(String... roles){return Arrays.asList(roles).contains(SecurityUtil.getCurrentUserRole());}
 private boolean authority(String permission){return SecurityUtil.getCurrentUser().getAuthorities().stream().anyMatch(a->permission.equals(a.getAuthority()));}
 public List<Tool> available(){
  if(!ALL.contains(identity.role()))throw denied();
  var list=new ArrayList<Tool>();
  list.add(tool("profile","我的身份","查看当前登录身份与服务门店",false,obj()));
  if(role("MEMBER")||authority("COURSE_READ"))list.add(tool("courses","课程查询","查询有权查看的课程；会员可看可预约课程，教练只看自己课程",false,obj("from",date("起始日期"),"to",date("结束日期"),"keyword",text("课程关键词",100),"page",integer("页码，每页20条，按pagination.nextPage继续",1,10000))));
  if(role("MEMBER","RECEPTION","MANAGER","SUPER_ADMIN","COACH"))list.add(tool("bookings","预约查询","查看本人或权限范围内的预约，不含完整会员隐私档案",false,obj("keyword",text("课程或会员关键词",100),"page",integer("页码，每页20条",1,10000))));
  if(role("MEMBER","COACH","MANAGER","SUPER_ADMIN"))list.add(tool("training_plans","训练计划","查看权限范围内的训练计划，会员不显示草稿",false,obj("keyword",text("计划关键词",100),"page",integer("页码",1,10000))));
  if(role("COACH"))list.add(tool("students","我的学员","查询与本人存在服务关系的学员",false,obj("keyword",text("学员关键词",100),"page",integer("页码",1,10000))));
  if(role("RECEPTION","MANAGER","SUPER_ADMIN"))list.add(tool("members","会员查询","查找权限门店内会员的编号和状态，用于办理预约",false,obj("keyword",text("会员姓名或编号",100),"page",integer("页码",1,10000))));
  if(role("SALES","MANAGER","SUPER_ADMIN")){
   list.add(tool("retention","续费与回访","查看本人负责或权限门店内的续费回访概况",false,obj("keyword",text("回访关键词",100),"page",integer("页码，每页20条",1,10000))));
   list.add(tool("leads","潜客查询","查询有权访问的潜客，可返回版本和接待顾问信息供体验预约使用",false,obj("keyword",text("潜客姓名关键词",100),"page",integer("页码",1,10000),"leadId",integer("潜客ID（填写后查询详情）",1,Long.MAX_VALUE))));
   list.add(tool("lead_options","接待顾问","查询可选接待顾问及所属门店",false,obj("page",integer("页码，每页20条",1,10000))));
  }
  if(role("RECEPTION","MANAGER","SUPER_ADMIN"))list.add(tool("cash_summary","交班与日结概况","只读查询门店交班情况，不代为收款、退款、交班或复核",false,obj("day",date("营业日期"),"page",integer("页码，每页20条",1,10000))));
  if(role("MANAGER","SUPER_ADMIN"))list.add(tool("analytics","经营概况","查询有权查看的门店经营汇总",false,obj("from",date("起始日期"),"to",date("结束日期"))));
  if(role("MEMBER","RECEPTION","MANAGER","SUPER_ADMIN")&&(role("MEMBER")||authority("BOOKING_WRITE"))){
   var p=obj("scheduleId",integer("排课ID",1,Long.MAX_VALUE),"entitlementId",integer("权益ID（付费课程按需填写）",1,Long.MAX_VALUE));
   var required=new ArrayList<>(List.of("scheduleId"));if(!role("MEMBER")){p.withObject("/properties").set("memberId",integer("会员ID",1,Long.MAX_VALUE));required.add("memberId");}
   list.add(tool("book_course","预约课程","仅创建待确认操作；确认后仍按真实名额、权益和权限办理",true,required(p,required.toArray(String[]::new))));
   var c=obj("bookingId",integer("预约ID",1,Long.MAX_VALUE));if(!role("MEMBER"))c.withObject("/properties").set("reason",text("取消原因（临时取消必填）",300));
   list.add(tool("cancel_booking","取消预约","确认后取消指定预约，沿用原有临时取消限制",true,required(c,"bookingId")));
  }
  if(role("SALES","MANAGER","SUPER_ADMIN")&&authority("LEAD_WRITE"))list.add(tool("book_trial","安排体验课","为有权接待的潜客安排独立体验预约，须确认版本、顾问和时间",true,required(obj("leadId",integer("潜客ID",1,Long.MAX_VALUE),"expectedVersion",integer("潜客当前版本",0,Long.MAX_VALUE),"hostId",integer("接待顾问ID",1,Long.MAX_VALUE),"startsAt",datetime("开始时间"),"endsAt",datetime("结束时间"),"content",text("体验内容",1000)),"leadId","expectedVersion","hostId","startsAt","endsAt","content")));
  if(role("COACH")&&authority("TRAINING_PLAN_WRITE")){
   var e=required(obj("name",text("动作名称",100),"mode",choice("动作模式","STRENGTH","DURATION"),"sets",integer("组数",1,20),"reps",integer("每组次数",1,200),"weightKg",number("重量（kg）",0,500),"minutes",integer("分钟",1,240),"note",text("动作说明",300)),"name","mode");
   var arr=json.createObjectNode().put("type","array").put("description","训练动作").put("minItems",1).put("maxItems",20);arr.set("items",e);
   list.add(tool("training_draft","创建训练草稿","为本人有服务关系的学员创建草稿，不发布、不签到、不扣课",true,required(obj("memberId",integer("学员ID",1,Long.MAX_VALUE),"title",text("计划标题",100),"goal",choice("训练目标","GENERAL","FAT_LOSS","MUSCLE_GAIN","POSTURE","ENDURANCE"),"startDate",date("计划开始日期"),"endDate",date("计划结束日期"),"note",text("计划备注",1000),"exercises",arr),"memberId","title","goal","startDate","endDate","exercises")));
  }
  return list;
 }
 public Tool get(String name){return available().stream().filter(t->t.name().equals(name)).findFirst().orElseThrow(AgentIdentity::denied);}
 public Tool validate(String name,JsonNode args,boolean write){var t=get(name);if(t.write()!=write)throw invalid(write?"此工具不提供写操作":"写操作必须先预览并确认");if(args==null||!args.isObject()||args.toString().length()>18000)throw invalid("工具参数须为有效对象且不可过长");validateValue(t.parameters(),args,0);return t;}
 private void validateValue(JsonNode s,JsonNode v,int depth){
  if(depth>5)throw invalid("参数嵌套过深");
  switch(s.path("type").asText()){
   case "object" -> {if(!v.isObject())throw invalid("参数须为对象");var p=s.path("properties");v.fieldNames().forEachRemaining(k->{if(!p.has(k))throw invalid("不支持的参数："+k.substring(0,Math.min(k.length(),50)));validateValue(p.get(k),v.get(k),depth+1);});for(var key:s.path("required"))if(!v.hasNonNull(key.asText()))throw invalid("请填写："+p.path(key.asText()).path("description").asText(key.asText()));}
   case "string" -> {if(!v.isTextual()||v.asText().isBlank()||v.asText().length()>s.path("maxLength").asInt(1000))throw invalid("文本参数为空或过长");if(s.has("enum")&&!contains(s.get("enum"),v))throw invalid("选项不正确");try{if("date".equals(s.path("format").asText()))java.time.LocalDate.parse(v.asText());if("date-time".equals(s.path("format").asText()))java.time.LocalDateTime.parse(v.asText());}catch(java.time.DateTimeException e){throw invalid("日期或时间格式不正确，请使用本地时间");}}
   case "integer" -> {if(!v.isIntegralNumber()||!v.canConvertToLong()||v.asLong()<s.path("minimum").asLong()||v.asLong()>s.path("maximum").asLong())throw invalid("整数参数超出允许范围");}
   case "number" -> {if(!v.isNumber()||v.decimalValue().compareTo(s.path("minimum").decimalValue())<0||v.decimalValue().compareTo(s.path("maximum").decimalValue())>0)throw invalid("数值超出允许范围");}
   case "array" -> {if(!v.isArray()||v.size()<s.path("minItems").asInt()||v.size()>s.path("maxItems").asInt())throw invalid("列表长度不正确");v.forEach(x->validateValue(s.get("items"),x,depth+1));}
   default -> throw invalid("不支持的参数类型");
  }
 }
 private static boolean contains(JsonNode a,JsonNode value){for(var n:a)if(n.equals(value))return true;return false;}
 private Tool tool(String n,String l,String d,boolean w,ObjectNode p){return new Tool(n,l,d,w,p);}
 private ObjectNode obj(Object... entries){var n=json.createObjectNode().put("type","object").put("additionalProperties",false);var p=n.putObject("properties");for(int i=0;i<entries.length;i+=2)p.set((String)entries[i],(JsonNode)entries[i+1]);n.putArray("required");return n;}
 private ObjectNode required(ObjectNode o,String... keys){var r=o.putArray("required");for(var k:keys)r.add(k);return o;}
 private ObjectNode text(String label,int max){return json.createObjectNode().put("type","string").put("description",label).put("maxLength",max);}
 private ObjectNode date(String label){return text(label,10).put("format","date");}
 private ObjectNode datetime(String label){return text(label,30).put("format","date-time");}
 private ObjectNode integer(String label,long min,long max){return json.createObjectNode().put("type","integer").put("description",label).put("minimum",min).put("maximum",max);}
 private ObjectNode number(String label,int min,int max){return json.createObjectNode().put("type","number").put("description",label).put("minimum",min).put("maximum",max);}
 private ObjectNode choice(String label,String... values){var n=text(label,30);var a=n.putArray("enum");for(var v:values)a.add(v);return n;}
}
