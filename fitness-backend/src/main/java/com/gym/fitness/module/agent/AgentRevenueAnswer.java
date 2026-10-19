package com.gym.fitness.module.agent;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.regex.*;
import static com.gym.fitness.module.agent.AgentIdentity.*;

/** Common revenue follow-ups need a scoped database read, not another model round. */
final class AgentRevenueAnswer {
 private AgentRevenueAnswer(){}
 private static final Pattern DATE=Pattern.compile("(?<!\\d)(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?(?!\\d)");
 private static final Pattern RECENT=Pattern.compile("(?:近|最近)(\\d{1,3})天");
 static boolean applicable(String text,AgentChatContext.Context context){
  if(text.matches("(?s).*(?:修改|删除|创建|新增|保存|发送|发消息|预约|取消|收款|退款|改成|更改|导出).*"))return false;
  if(text.matches("(?s).*(?:营收|营业额|销售额|收入|经营概况).*"))return true;
  boolean lastAnalytics=!AgentGuidance.isPrevious(context)&&!context.queries().isEmpty()&&"analytics".equals(context.queries().get(context.queries().size()-1).tool());
  return ("analytics".equals(context.pendingTool())||lastAnalytics)&&
    (text.matches("(?s).*(?:具体数字|具体金额|多少钱|多少元|再说一遍|统计范围|开始日期|结束日期).*" )||
      text.matches("[\\d\\s年月日/.:至到~—－-]+")||text.matches("(?:查|看|查询|查看|统计|那|改为|换成|按)?(?:今天|昨天|本周|上周|本月|上月|最近\\d{1,3}天|近\\d{1,3}天)[。？?！! ]*"));
 }
 static ObjectNode range(String text,AgentChatContext.Context context,ObjectMapper json){
  var dates=new ArrayList<LocalDate>();var matcher=DATE.matcher(text);
  try{while(matcher.find())dates.add(LocalDate.of(Integer.parseInt(matcher.group(1)),Integer.parseInt(matcher.group(2)),Integer.parseInt(matcher.group(3))));}
  catch(DateTimeException e){throw invalid("日期不存在，请提供有效的开始日期和结束日期（YYYY-MM-DD）");}
  LocalDate start=null,end=null,today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
  if(dates.size()>2)throw invalid("请一次提供一个统计区间的开始日期和结束日期");
  if(dates.size()==1&&text.matches("(?s).*(?:开始日期|结束日期|至|到|~|—).*"))return null;
  if(!dates.isEmpty()){start=dates.get(0);end=dates.get(dates.size()-1);}
  else if(text.contains("昨天")){start=today.minusDays(1);end=start;}
  else if(text.contains("今天")){start=today;end=today;}
  else if(text.contains("上周")){start=today.with(DayOfWeek.MONDAY).minusWeeks(1);end=start.plusDays(6);}
  else if(text.contains("本周")){start=today.with(DayOfWeek.MONDAY);end=today;}
  else if(text.contains("上月")){start=today.minusMonths(1).withDayOfMonth(1);end=start.with(TemporalAdjusters.lastDayOfMonth());}
  else if(text.contains("本月")){start=today.withDayOfMonth(1);end=today;}
  else{
   var recent=RECENT.matcher(text);
   if(recent.find()){int days=Integer.parseInt(recent.group(1));if(days<1||days>93)throw invalid("营收查询周期须为1至93天");start=today.minusDays(days-1);end=today;}
   else if(!text.matches("(?s).*(?:今年|去年|季度|下周|下月|\\d+月|\\d+天|开始日期|结束日期).*")){
    var old=context.queries().stream().filter(q->"analytics".equals(q.tool())).findFirst();
    if(old.isPresent())return (ObjectNode)old.get().arguments().deepCopy();
    if(text.contains("经营概况")){start=today.minusDays(29);end=today;}
   }
  }
  if(start==null)return null;
  if(start.isAfter(end)||ChronoUnit.DAYS.between(start,end)>92)throw invalid("查询周期须在93天内且开始日期不晚于结束日期");
  return json.createObjectNode().put("from",start.toString()).put("to",end.toString());
 }
 static String answer(AgentDtos.ToolResult receipt){
  var data=receipt.data();
  if(data==null||!data.path("revenue").isNumber()||!data.path("from").isTextual()||!data.path("to").isTextual()||receipt.truncated())
   return "本次经营查询未返回完整的营收金额和统计范围，暂不能提供准确数字。请重新查询或查看经营分析页面。";
  return "统计范围："+data.path("from").asText()+" 至 "+data.path("to").asText()+"（含首尾日期）。\n"+
   "当前账号可见范围内，区间营收为 "+data.get("revenue").decimalValue().setScale(2,RoundingMode.HALF_UP).toPlainString()+" 元。\n"+
   "口径：区间内销售订单登记的实付金额合计，不等同于扣除退款后的净收款。\n查询时间："+receipt.asOf()+"；来源："+receipt.source()+"。";
 }
 static String askRange(){return "你要查询哪段时间的营收？请提供开始日期和结束日期，例如“2026-10-01至2026-10-19”，也可以说“本月”或“最近30天”。";}
}
