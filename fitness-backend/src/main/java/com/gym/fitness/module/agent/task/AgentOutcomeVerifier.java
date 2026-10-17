package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.module.agent.computer.*;
import com.gym.fitness.module.course.service.CourseService;
import com.gym.fitness.module.chat.service.ChatService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.*;
import java.util.regex.*;
import static com.gym.fitness.module.agent.AgentIdentity.invalid;

/** Completion is computed, never taken from the model's finish or a browser success flag. */
@Service @RequiredArgsConstructor
public class AgentOutcomeVerifier {
    private final AgentTaskStore store;private final AgentMutationReceiptService receipts;private final ComputerAgentService computer;
    private final CourseService courses;private final ChatService chat;private final ObjectMapper json;private final AgentDomainVerifier domains;
    public record Outcome(String id,String label,String status,String evidence){}
    public record Report(boolean complete,boolean partial,List<Outcome> outcomes,AgentTaskStore.View task){}
    public Report verify(ComputerAgentDtos.Session me,String id,long version,String document,JsonNode context){
        var task=store.check(me,id,version,document);if(context==null||!context.isObject()||context.toString().length()>16000)throw invalid("页面核验上下文不正确");
        String goal=task.goal();var received=receipts.list(me,id).stream().filter(r->"SUCCEEDED".equals(r.status())).toList();
        var outcomes=new ArrayList<Outcome>();var used=new HashSet<String>();
        String priorClause="";
        for(String clause:AgentGoalClauses.parse(goal)){
            clause=clause.replaceFirst("(?:但|并且|并)?(?:不要|不必|无需|不提交|不保存|不发布|不发送|保持不变|其他字段不变|等待我确认|结束任务).*", "").trim();
            if(clause.isBlank()||clause.matches("(?:保存后|确认后|核对成功后|看到列表后|看到.*页面后)"))continue;
            int start=outcomes.size();String targetContext=AgentGoalClauses.fieldOnly(clause)?clause+" "+priorClause:clause;priorClause=clause;
            Matcher fee=Pattern.compile("(?:价格|费用|售价).*?(?:改为|改成|改到|设为|设成|调整为|调整到|调整成|修改为)\\s*[¥￥]?\\s*(\\d+(?:\\.\\d{1,2})?)").matcher(clause);
            Matcher capacity=Pattern.compile("容量.*?(?:改为|改成|改到|设为|设成|调整为|调整到|调整成)\\s*(\\d+)").matcher(clause);
            boolean fieldChange=false;
            if(fee.find()){fieldChange=true;add(outcomes,clause,verifiedCourse(received,"fee",fee.group(1),targetContext),"课程成功回执及当前价格回查");}
            if(capacity.find()){fieldChange=true;add(outcomes,clause,verifiedCourse(received,"maxCapacity",capacity.group(1),targetContext),"课程成功回执及当前容量回查");}
            if(!fieldChange&&isMutation(clause)){
                String type=clause.matches(".*(?:发送|发(?:个|一条)?消息|发给).*" )?"message":clause.contains("体验")?"trial":clause.contains("潜客")?"lead":clause.matches(".*(?:训练|计划|动作|反馈).*" )?"training":clause.matches(".*(?:预约|签到).*" )?"booking":clause.matches(".*(?:回访|续费).*" )?"retention":clause.contains("会员")?"member":clause.contains("排课")?"schedule":clause.contains("课程")?"course":"unknown";
                add(outcomes,clause,verifiedMutation(received,type,clause,used,"MEMBER".equals(me.role())),"已授权业务服务提交回执及对象核对");
            }
            if(!fieldChange&&clause.matches(".*(?:打开|进入|切换到|点击|点开).*")){
                String path=destination(clause,me.role());boolean matched=path!=null&&path.equals(context.path("path").asText());
                if(matched&&"MEMBER".equals(me.role())){String tab=clause.contains("训练")?"训练":clause.contains("消息")?"消息":clause.contains("预约")?"预约":clause.contains("课程")?"课程":"";if(!tab.isEmpty()){matched=false;for(var t:context.path("selectedTabs"))if(t.asText().contains(tab))matched=true;}}
                add(outcomes,clause,matched,"当前网页入口及选中页签");
            }
            if(!fieldChange&&!isMutation(clause)&&clause.matches(".*(?:查询|统计|告诉我|有哪些|多少|几节|几次|查看).*")){
                String topic=clause.contains("身份")?"profile":clause.contains("接待顾问")?"lead_options":clause.matches(".*(?:课程|排课|几节).*" )?"courses":clause.contains("预约")?"bookings":clause.contains("训练")?"training_plans":clause.contains("学员")?"students":clause.matches(".*(?:潜客|体验).*" )?"leads":clause.matches(".*(?:续费|回访).*" )?"retention":clause.matches(".*(?:交班|日结).*" )?"cash_summary":clause.matches(".*(?:经营|收入|营业).*" )?"analytics":clause.contains("会员")?"members":"page";
                boolean found=false;var groups=new HashMap<String,List<JsonNode>>();for(var proof:task.checkpoint().path("queryProofs"))if(topic.equals(proof.path("tool").asText())&&fresh(proof)){
                    boolean dates=true;if((clause.contains("本周")||clause.contains("下周"))&&Set.of("courses","analytics").contains(topic)){var monday=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).with(java.time.DayOfWeek.MONDAY);if(clause.contains("下周"))monday=monday.plusDays(7);dates=monday.toString().equals(proof.path("arguments").path("from").asText())&&monday.plusDays(6).toString().equals(proof.path("arguments").path("to").asText());}
                    var keyword=Pattern.compile("(?:姓名为|名称为|关键词[为是]?|叫做)[‘“\"「]?([^的，,。；;‘’“”\"「」]+)").matcher(clause);
                    if(keyword.find()&&!keyword.group(1).trim().equals(proof.path("arguments").path("keyword").asText()))dates=false;
                    String filter=proof.path("arguments").path("keyword").asText();if(!filter.isBlank()&&!clause.contains(filter))dates=false;
                    if(dates){if(proof.path("pagination").isObject()){var args=new TreeMap<String,String>();proof.path("arguments").fields().forEachRemaining(e->{if(!"page".equals(e.getKey()))args.put(e.getKey(),e.getValue().asText());});groups.computeIfAbsent(args.toString(),k->new ArrayList<>()).add(proof.path("pagination"));}else if(!proof.path("truncated").asBoolean(true))found=true;}
                }
                if(groups.values().stream().anyMatch(this::completePages))found=true;
                add(outcomes,clause,found,"服务端真实查询记录及范围");
            }
            if(outcomes.size()==start)add(outcomes,clause,false,"尚无已注册的目标核验条件");
        }
        if(outcomes.isEmpty())add(outcomes,"目标尚无已注册核验条件，请补充具体业务对象或操作",false,"");
        boolean complete=outcomes.stream().allMatch(o->"SATISFIED".equals(o.status()));boolean partial=!complete&&outcomes.stream().anyMatch(o->"SATISFIED".equals(o.status()));
        var checkpoint=task.checkpoint().deepCopy();if(checkpoint.isObject())((com.fasterxml.jackson.databind.node.ObjectNode)checkpoint).set("verification",json.valueToTree(outcomes));
        var saved=store.update(me,id,version,document,complete?"SUCCEEDED":"RUNNING",task.plan(),checkpoint);
        return new Report(complete,partial,List.copyOf(outcomes),saved);
    }
    private boolean completePages(List<JsonNode> proofs){
        if(proofs.isEmpty())return false;var latest=proofs.get(proofs.size()-1);long total=latest.path("total").asLong(-1);int size=latest.path("size").asInt();if(total<0||size<1||size>20)return false;
        long count=Math.max(1,(total+size-1)/size);if(count>12)return false;var pages=new HashMap<Integer,JsonNode>();for(var p:proofs)if(p.path("total").asLong()==total&&p.path("size").asInt()==size)pages.put(p.path("page").asInt(),p);
        var keys=new HashSet<String>();for(int page=1;page<=count;page++){var p=pages.get(page);if(p==null||p.path("contentTruncated").asBoolean(true))return false;int expected=(int)Math.min(size,Math.max(0,total-(long)(page-1)*size));if(p.path("returned").asInt(-1)!=expected||!p.path("keys").isArray()||p.path("keys").size()!=expected)return false;for(var key:p.path("keys"))if(!keys.add(key.asText()))return false;}
        return keys.size()==total;
    }
    private void add(List<Outcome> list,String label,boolean satisfied,String evidence){list.add(new Outcome("g"+(list.size()+1),label,satisfied?"SATISFIED":"UNSATISFIED",satisfied?evidence:"缺少对应目标的真实证据"));}
    private boolean fresh(JsonNode proof){try{var asOf=java.time.LocalDateTime.parse(proof.path("asOf").asText());var now=java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));return !asOf.isBefore(now.minusMinutes(5))&&!asOf.isAfter(now.plusSeconds(10));}catch(java.time.DateTimeException invalid){return false;}}
    private boolean verifiedCourse(List<AgentMutationReceiptService.Receipt> received,String field,String expected,String goal){
        for(var r:received){
            if(!r.path().matches("/api/v1/admin/courses/\\d+")||!"PUT".equals(r.method()))continue;
            JsonNode data=r.result().path("data");if(!data.path(field).isNumber()||data.path(field).decimalValue().compareTo(new java.math.BigDecimal(expected))!=0)continue;
            long id=Long.parseLong(r.path().substring(r.path().lastIndexOf('/')+1));
            for(var c:courses.listCourses())if(c.getId()==id&&AgentGoalBinding.labelMatches(goal,c.getName(),id)){
                var value="fee".equals(field)?c.getFee():c.getMaxCapacity()==null?null:new java.math.BigDecimal(c.getMaxCapacity());
                if(value!=null&&value.compareTo(new java.math.BigDecimal(expected))==0)return true;
            }
        }return false;
    }
    private boolean verifiedMutation(List<AgentMutationReceiptService.Receipt> received,String type,String clause,Set<String> used,boolean member){
        for(var r:received){
            if(used.contains(r.id()))continue;
            if("message".equals(type)&&r.path().equals("/api/v1/messages/send")){
                long recipient=r.request().path("recipientUserId").asLong(),id=r.result().path("data").path("id").asLong();
                if(id>0&&chat.thread(recipient).messages().stream().anyMatch(m->m.id()==id&&m.mine()&&AgentGoalBinding.messageMatches(clause,r.before().path("recipientName").asText(),recipient,m.content()))){used.add(r.id());return true;}
            }
            boolean path=switch(type){case "trial"->r.path().matches("/api/v1/leads/\\d+/trials(/\\d+/result)?");case "lead"->r.path().startsWith("/api/v1/leads");case "training"->r.path().contains("/training-plans");case "booking"->r.path().contains("/bookings");case "retention"->r.path().matches("/api/v1/admin/members/\\d+/follow-ups");case "member"->r.path().matches("/api/v1/admin/members(/\\d+)?");case "course"->r.path().matches("/api/v1/admin/courses(/\\d+)?");case "schedule"->r.path().startsWith("/api/v1/admin/schedules");default->false;};
            boolean operation=(!clause.contains("发布")||r.path().endsWith("/publish"))&&(!clause.contains("归档")||r.path().endsWith("/archive"))&&(!clause.contains("取消")||r.path().endsWith("/cancel"))&&(!clause.contains("签到")||r.path().endsWith("/check-in"));
            if(path&&operation&&r.result().path("code").asInt(-1)==0&&domains.verify(type,r,clause,member)){used.add(r.id());return true;}
        }return false;
    }
    private boolean isMutation(String text){return text.matches(".*(?:新建|新增|创建|修改|改为|改成|设为|更新|删除|提交|保存|发送|发(?:个|一条)?消息|发给|发布|预约|签到|录入|登记|添加跟进|安排.*体验|归档.*计划|提交.*反馈).*" )&&!text.matches("^(?:查看|查询|统计).*" )&&!text.matches("^(?:打开|进入|切换到|点击|点开)(?:我的)?(?:预约与签到|预约|课程预约|训练计划|我的训练)$");}
    private String destination(String text,String role){
        if("MEMBER".equals(role))return "/member-home";
        if(text.contains("训练"))return "/training-plans";if(text.contains("站内消息"))return "/chat";if(text.contains("周课表"))return "/schedule-week";
        if(text.matches(".*(?:课程与排课|课程库).*"))return "/courses";if(text.matches(".*(?:会员列表|会员管理).*"))return "/members";if(text.matches(".*(?:潜客|体验).*"))return "/leads";if(text.matches(".*(?:续费|回访).*"))return "/retention";if(text.matches(".*(?:交班|日结).*"))return "/cash-shifts";if(text.contains("经营分析"))return "/analytics";if(text.matches(".*(?:经营看板|总部总览|经营概况).*"))return "/dashboard";return null;
    }
}
