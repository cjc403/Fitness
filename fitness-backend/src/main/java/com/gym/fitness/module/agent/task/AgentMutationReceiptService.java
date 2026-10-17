package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import static com.gym.fitness.module.agent.AgentIdentity.*;

/** Original business service and its authoritative receipt commit in the same transaction. */
@Service
public class AgentMutationReceiptService {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final ObjectMapper json;private final AgentTaskStore tasks;private final com.gym.fitness.module.chat.service.ChatService chat;private final AgentDomainVerifier domains;
    public AgentMutationReceiptService(JdbcTemplate jdbc,PlatformTransactionManager manager,ObjectMapper json,AgentTaskStore tasks,com.gym.fitness.module.chat.service.ChatService chat,AgentDomainVerifier domains){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.tx.setTimeout(25);this.json=json;this.tasks=tasks;this.chat=chat;this.domains=domains;}
    public record Receipt(String id,String taskId,long taskVersion,String method,String path,String status,JsonNode request,JsonNode before,JsonNode result,LocalDateTime expiresAt){}
    private record Row(Receipt view,String document,String hash){}
    private static final Set<String> SAFE=Set.of("id","memberId","scheduleId","courseId","recipientUserId","venueId","version","expectedVersion","status","fee","maxCapacity","durationMinutes","intensity","type","name","title","startDate","endDate","startsAt","endsAt","scheduledStartTime","scheduledEndTime","plan","lead","booking","trial","data","message","success","code","goal","exercises","mode","sets","reps","minutes","weightKg","actualSets","actualReps","actualMinutes","actualWeightKg","hostId","coachId","roomId","maxStudents","trials","sessions","records","rating");
    public Receipt prepare(Session me,String task,long version,String document,String method,String path,JsonNode body){
        method=method.toUpperCase(Locale.ROOT);var target=AgentRequestTarget.parse(method,path);if(!supported(method,target.path()))throw invalid("此操作尚未注册为可核验网页技能，请使用原业务页面手动办理");
        if(body==null||!(body.isObject()||body.isNull())||body.toString().length()>65536)throw invalid("只支持有界JSON请求");
        final String verb=method;
        final String route=target.path();
        return tx.execute(ignored->{
            tasks.get(me,task);lockTask(task);var current=tasks.check(me,task,version,document);if(!"RUNNING".equals(current.status()))throw invalid("任务当前不可提交");
            Integer pending=jdbc.queryForObject("SELECT count(*) FROM agent_task_receipt WHERE task_id=? AND status IN ('PENDING','APPROVED') AND expires_at>?",Integer.class,task,LocalDateTime.now());if(pending!=null&&pending>0)throw invalid("已有待确认请求，请先核对");
            Integer duplicate=jdbc.queryForObject("SELECT count(*) FROM agent_task_receipt WHERE task_id=? AND method=? AND path=? AND request_hash=? AND status='SUCCEEDED'",Integer.class,task,verb,route,fingerprint(body,target));if(duplicate!=null&&duplicate>0)throw invalid("该请求已经成功提交，禁止重复办理；请核对任务回执");
            JsonNode before=snapshot(me,verb,route,false,body);guardGoal(me,current.goal(),verb,route,body,before);
            if(!route.contains("/courses")&&!route.equals("/api/v1/messages/send"))domains.guard(current.goal(),before,body);
            if(route.equals("/api/v1/messages/send")){
                var contacts=chat.contacts();var contact=java.util.stream.Stream.concat(contacts.staff().stream(),contacts.members().stream()).filter(c->c.userId()==body.path("recipientUserId").asLong()).findFirst().orElseThrow(com.gym.fitness.module.agent.AgentIdentity::denied);
                if(!AgentGoalBinding.messageMatches(current.goal(),contact.name(),contact.userId(),body.path("content").asText()))throw invalid("收件人或消息内容与任务要求不一致，请补充明确收件人及完整内容");
                ((ObjectNode)before).put("recipientName",contact.name()).put("recipientUserId",contact.userId());
            }
            String id=UUID.randomUUID().toString();var now=LocalDateTime.now();
            JsonNode summary=project(body);if(!target.query().isEmpty()){if(!summary.isObject())summary=json.createObjectNode();((ObjectNode)summary).put("queryHash",digest(target.query()));}
            jdbc.update("INSERT INTO agent_task_receipt(id,task_id,task_version,document_nonce,method,path,request_hash,request_summary,before_json,status,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?,?)",id,task,version,document,verb,route,fingerprint(body,target),write(summary),write(before),now,now.plusMinutes(2));return get(me,id);
        });
    }
    public Receipt get(Session me,String id){var row=row(id,false);tasks.get(me,row.view.taskId());return row.view;}
    public List<Receipt> list(Session me,String task){tasks.get(me,task);return jdbc.query("SELECT id FROM agent_task_receipt WHERE task_id=? ORDER BY created_at",(r,i)->get(me,r.getString(1)),task);}
    public void revokePending(Session me,String task){tasks.get(me,task);jdbc.update("UPDATE agent_task_receipt SET status='EXPIRED' WHERE task_id=? AND status IN ('PENDING','APPROVED')",task);}
    public Receipt approve(Session me,String id,long version,String document){var initial=get(me,id);return tx.execute(ignored->{lockTask(initial.taskId());var row=row(id,true);tasks.check(me,row.view.taskId(),version,document);if(row.view.taskVersion()!=version||!row.document.equals(document))throw invalid("旧目标或网页的确认已失效");if("APPROVED".equals(row.view.status())){valid(row,"APPROVED");return row.view;}valid(row,"PENDING");jdbc.update("UPDATE agent_task_receipt SET status='APPROVED' WHERE id=? AND status='PENDING'",id);return get(me,id);});}
    public JsonNode execute(Session me,String id,String method,String path,JsonNode body,Supplier<JsonNode> work){
        var initial=get(me,id);var target=AgentRequestTarget.parse(method,path);
        try{return tx.execute(ignored->{
            lockTask(initial.taskId());var row=row(id,true);var receipt=row.view;var task=tasks.check(me,receipt.taskId(),receipt.taskVersion(),row.document);
            if(!"RUNNING".equals(task.status()))throw invalid("任务已暂停或结束");valid(row,"APPROVED");
            if(!receipt.method().equals(method)||!receipt.path().equals(target.path())||!row.hash.equals(fingerprint(body,target)))throw invalid("请求与已确认的冻结内容不一致");
            if(path.matches("/api/v1/admin/courses/\\d+")&&!digest(receipt.before()).equals(digest(snapshot(me,method,path,true,body))))throw invalid("业务对象已经变化，请重新观察和确认");
            JsonNode result=work.get();if(result==null||!result.path("code").isIntegralNumber()||result.path("code").asInt()!=0)throw invalid("业务未成功，已回滚，禁止自动重发");
            jdbc.update("UPDATE agent_task_receipt SET status='SUCCEEDED',result_json=? WHERE id=?",write(project(result)),id);
            return result;
        });}catch(RuntimeException e){tx.execute(ignored->{var row=row(id,true);tasks.get(me,row.view.taskId());if("APPROVED".equals(row.view.status()))jdbc.update("UPDATE agent_task_receipt SET status='FAILED' WHERE id=?",id);return null;});throw e;}
    }
    private void valid(Row row,String expected){if(!expected.equals(row.view.status())||!row.view.expiresAt().isAfter(LocalDateTime.now()))throw invalid("提交许可已消费或过期，请核对原业务结果");}
    private Row row(String id,boolean lock){if(id==null||!id.matches("[a-f0-9-]{36}"))throw denied();var rows=jdbc.query("SELECT * FROM agent_task_receipt WHERE id=?"+(lock?" FOR UPDATE":""),(r,i)->new Row(new Receipt(r.getString("id"),r.getString("task_id"),r.getLong("task_version"),r.getString("method"),r.getString("path"),r.getString("status"),read(r.getString("request_summary")),read(r.getString("before_json")),r.getString("result_json")==null?NullNode.instance:read(r.getString("result_json")),r.getTimestamp("expires_at").toLocalDateTime()),r.getString("document_nonce"),r.getString("request_hash")),id);if(rows.size()!=1)throw denied();return rows.get(0);}
    private JsonNode snapshot(Session me,String method,String path,boolean lock,JsonNode body){
        if(path.matches("/api/v1/admin/courses/\\d+")&&!"POST".equals(method)){
            if(!me.permissions().contains("COURSE_WRITE"))throw denied();long id=Long.parseLong(path.substring(path.lastIndexOf('/')+1));
            var rows=jdbc.queryForList("SELECT id,name,type,venue_id,description,duration_minutes,max_capacity,fee,intensity FROM course WHERE id=?"+(lock?" FOR UPDATE":""),id);
            if(rows.size()!=1)throw denied();var data=rows.get(0);if(!"SUPER_ADMIN".equals(me.role())&&((Number)data.get("venue_id")).longValue()!=me.venueId())throw denied();var normalized=new TreeMap<String,Object>();data.forEach((k,v)->normalized.put(k.toLowerCase(Locale.ROOT),v));return json.valueToTree(normalized);
        }
        return domains.before(me,method,path,body);
    }
    private boolean supported(String method,String path){
        if(path==null||path.length()>500||path.contains("?")||path.contains("%")||!Set.of("POST","PUT","DELETE").contains(method))return false;
        return path.matches("/api/v1/admin/(courses|schedules)(/\\d+)?")||path.matches("/api/v1/messages/send")
            ||path.matches("/api/v1/(admin|member)/bookings(/\\d+/(cancel|check-in|leave|no-show))?")
            ||path.matches("/api/v1/leads(/\\d+(/(profile|follow-ups|trials)|/trials/\\d+/result))?")
            ||path.matches("/api/v1/(member/)?training-plans(/\\d+(/(draft|publish|archive|sessions)|/sessions/\\d+/(feedback|void)))?")
            ||path.matches("/api/v1/admin/members(/\\d+(/follow-ups)?)?");
    }
    private String fingerprint(JsonNode body,AgentRequestTarget.Target target){if(target.query().isEmpty())return digest(body);var value=json.createObjectNode();value.set("body",body);value.set("query",target.query());return digest(value);}
    private void lockTask(String id){jdbc.queryForList("SELECT id FROM agent_task WHERE id=? FOR UPDATE",id);}
    private void guardGoal(Session me,String goal,String method,String path,JsonNode body,JsonNode before){
        if(!goal.matches("(?s).*(?:新建|新增|创建|修改|改为|改成|调整|设为|更新|删除|提交|保存|发送|发(?:个|一条)?消息|发给|发布|预约|签到|录入|登记|安排|跟进|归档|反馈).*"))throw invalid("当前任务没有业务写入目标");
        if(path.equals("/api/v1/messages/send")&&!goal.matches("(?s).*(?:发送|发(?:个|一条)?消息|发给).*"))throw invalid("当前任务没有发送消息目标");
        if(path.startsWith("/api/v1/admin/members")&&!goal.matches("(?s).*(?:会员|回访|续费).*"))throw invalid("当前任务没有会员操作目标");
        if(path.startsWith("/api/v1/leads")&&!goal.matches("(?s).*(?:潜客|体验).*"))throw invalid("当前任务没有潜客或体验目标");
        if(path.contains("training-plans")&&!goal.matches("(?s).*(?:训练|计划|反馈).*"))throw invalid("当前任务没有训练操作目标");
        if(goal.matches(".*(?:不要提交|不要保存|不提交|不保存).*"))throw invalid("用户要求不提交，已阻止业务写入");
        if("DELETE".equals(method)&&!goal.matches(".*(?:删除|移除).*"))throw invalid("用户没有要求删除对象");
        if(path.endsWith("/publish")&&(!goal.contains("发布")||goal.matches(".*(?:不发布|不要发布).*")))throw invalid("用户未授权发布计划");
        if(path.endsWith("/send")&&goal.matches(".*(?:不发送|不要发送).*"))throw invalid("用户要求不发送消息");
        if(path.matches("/api/v1/admin/courses/\\d+")&&"PUT".equals(method)){
            String name=before.path("name").asText();String id=before.path("id").asText();Integer matching="SUPER_ADMIN".equals(me.role())?jdbc.queryForObject("SELECT count(*) FROM course WHERE name=?",Integer.class,name):jdbc.queryForObject("SELECT count(*) FROM course WHERE name=? AND venue_id=?",Integer.class,name,me.venueId());if(matching!=null&&matching>1&&!AgentGoalBinding.labelMatches(goal,null,Long.parseLong(id),"course"))throw invalid("当前范围有同名课程，请补充目标课程编号；不会猜测要修改哪一条");
            if(!goal.contains(name)&&!goal.matches("(?s).*(?:编号|#)\\s*"+java.util.regex.Pattern.quote(id)+"(?:\\D|$).*"))throw invalid("课程编号与用户目标不匹配，请先定位唯一课程");
            goal=AgentGoalClauses.courseTarget(goal,name,Long.parseLong(id));
            var matcher=java.util.regex.Pattern.compile("(?:价格|费用|售价).*?(?:改为|改成|改到|设为|设成|调整为|调整到|调整成|修改为)\\s*[¥￥]?\\s*(\\d+(?:\\.\\d{1,2})?)").matcher(goal);String expected=null;while(matcher.find())expected=matcher.group(1);
            if(expected!=null&&(!body.path("fee").isNumber()||body.path("fee").decimalValue().compareTo(new java.math.BigDecimal(expected))!=0))throw invalid("拟提交价格与用户要求不一致");
            var fields=Map.of("name","name","type","type","description","description","durationMinutes","duration_minutes","maxCapacity","max_capacity","intensity","intensity","venueId","venue_id","fee","fee");
            var labels=Map.of("name","名称","type","类型","description","简介","durationMinutes","时长","maxCapacity","容量","intensity","强度","venueId","所属","fee","(?:价格|费用|售价)");
            for(var field:fields.entrySet())if(body.has(field.getKey())){
                String old=before.path(field.getValue()).isNull()?"":before.path(field.getValue()).asText(),next=body.path(field.getKey()).isNull()?"":body.path(field.getKey()).asText();
                boolean same=old.equals(next)||(before.path(field.getValue()).isNumber()&&body.path(field.getKey()).isNumber()&&before.path(field.getValue()).decimalValue().compareTo(body.path(field.getKey()).decimalValue())==0);
                if(!same&&!goal.matches("(?s).*"+labels.get(field.getKey())+"[^，,。；;\\n]*(?:改|调整|设).*"))throw invalid("用户未要求修改"+labels.get(field.getKey())+"，已阻止附带变更");
                if(!same&&Set.of("maxCapacity","durationMinutes").contains(field.getKey())){
                    var number=java.util.regex.Pattern.compile(labels.get(field.getKey())+".*?(?:改为|改成|改到|设为|设成|调整为|调整到|调整成)\\s*(\\d+)").matcher(goal);
                    if(!number.find()||!body.get(field.getKey()).isNumber()||body.get(field.getKey()).asInt()!=Integer.parseInt(number.group(1)))throw invalid("拟提交数值与用户要求不一致");
                }
            }
        }
    }
    private JsonNode project(JsonNode n){
        if(n.isObject()){var o=json.createObjectNode();n.fields().forEachRemaining(e->{if(SAFE.contains(e.getKey()))o.set(e.getKey(),project(e.getValue()));else if(Set.of("content","note","feedbackContent").contains(e.getKey())){var value=e.getValue();if(value.isTextual())value=value.asText().isBlank()?NullNode.instance:TextNode.valueOf(value.asText().trim());o.put(e.getKey()+"Hash",digest(value));}});return o;}
        if(n.isArray()){var a=json.createArrayNode();for(int i=0;i<Math.min(n.size(),100);i++)a.add(project(n.get(i)));return a;}
        return n.deepCopy();
    }
    private JsonNode canonical(JsonNode n){if(n.isObject()){var o=json.createObjectNode();var keys=new TreeSet<String>();n.fieldNames().forEachRemaining(keys::add);keys.forEach(k->o.set(k,canonical(n.get(k))));return o;}if(n.isArray()){var a=json.createArrayNode();n.forEach(v->a.add(canonical(v)));return a;}if(n.isNumber())return DecimalNode.valueOf(n.decimalValue().stripTrailingZeros());return n;}
    private String digest(JsonNode n){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(canonical(n)).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private JsonNode read(String s){try{return json.readTree(s);}catch(Exception e){throw new IllegalStateException("无法读取提交回执");}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw invalid("提交回执格式不正确");}}
}
