package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.module.agent.computer.ComputerAgentDtos.Session;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static com.gym.fitness.module.agent.AgentIdentity.*;

/** Server-owned task versions; browser progress is never an authoritative business receipt. */
@Repository
public class AgentTaskStore {
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final ObjectMapper json;
    private static final Set<String> STATES=Set.of("PLANNING","RUNNING","RECOVERING","WAITING_CONFIRMATION","WAITING_INPUT","PAUSED","VERIFYING","SUCCEEDED","PARTIAL","FAILED","CANCELLED");
    private static final Set<String> TERMINAL=Set.of("SUCCEEDED","PARTIAL","FAILED","CANCELLED");
    public AgentTaskStore(JdbcTemplate jdbc,PlatformTransactionManager manager,ObjectMapper json){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.json=json;}
    public record View(String id,long version,String goal,String status,JsonNode plan,JsonNode checkpoint,LocalDateTime createdAt,LocalDateTime updatedAt){}
    private record Row(View view,long user,long venue,String role,String session,String document){}
    public View create(Session me,String document,String goal){
        validDocument(document);validGoal(goal);
        return tx.execute(ignored->{
            jdbc.queryForList("SELECT id FROM sys_user WHERE id=? FOR UPDATE",me.userId());
            Integer active=jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE user_id=? AND session_hash=? AND status NOT IN ('SUCCEEDED','PARTIAL','FAILED','CANCELLED') AND updated_at>?",Integer.class,me.userId(),me.sessionKey(),LocalDateTime.now().minusMinutes(30));
            if(active!=null&&active>0)throw invalid("已有未结束的任务，请恢复或停止该任务后继续");
            var now=LocalDateTime.now();String id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO agent_task(id,user_id,venue_id,role,session_hash,document_nonce,version,goal,status,plan_json,checkpoint_json,created_at,updated_at) VALUES(?,?,?,?,?,?,0,?,'RUNNING','{}','{}',?,?)",id,me.userId(),me.venueId(),me.role(),me.sessionKey(),document,goal.trim(),now,now);
            return get(me,id);
        });
    }
    public View get(Session me,String id){return owned(me,id,false).view;}
    public boolean schemaAvailable(){try{jdbc.queryForObject("SELECT count(*) FROM agent_task WHERE 1=0",Integer.class);jdbc.queryForObject("SELECT count(*) FROM agent_task_receipt WHERE 1=0",Integer.class);return true;}catch(org.springframework.dao.DataAccessException unavailable){return false;}}
    public int reserveModelCall(Session me,String id,long expected,String document){
        return tx.execute(ignored->{var row=owned(me,id,true);checkVersion(row,expected,document);if(!"RUNNING".equals(row.view.status()))throw invalid("任务当前不可规划");
            int last=row.view.checkpoint().path("modelCalls").asInt();
            for(var detail:jdbc.queryForList("SELECT detail_json FROM agent_task_step WHERE task_id=? AND kind='MODEL_CALL'",String.class,id))last=Math.max(last,decode(detail).path("call").asInt());
            if(last>=100||row.view.checkpoint().path("totalTokens").asInt()>=100000)throw invalid("任务规划预算已用尽，请核对已有结果");
            int next=last+1;jdbc.update("INSERT INTO agent_task_step(id,task_id,task_version,kind,detail_json,created_at) VALUES(?,?,?,'MODEL_CALL',?,?)",UUID.randomUUID().toString(),id,expected,encode(Map.of("call",next)),LocalDateTime.now());return next;
        });
    }
    public void recordModelUsage(Session me,String id,int call,int tokens){
        if(call<1||call>100||tokens<0||tokens>1000000)throw invalid("模型用量格式不正确");
        tx.execute(ignored->{var row=owned(me,id,true);boolean reserved=jdbc.queryForList("SELECT detail_json FROM agent_task_step WHERE task_id=? AND kind='MODEL_CALL'",String.class,id).stream().anyMatch(s->decode(s).path("call").asInt()==call);if(!reserved)throw invalid("模型调用没有预留记录");
            String event=UUID.nameUUIDFromBytes(("usage:"+id+":"+call).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            var existing=jdbc.queryForList("SELECT detail_json FROM agent_task_step WHERE id=?",String.class,event);if(!existing.isEmpty()){if(decode(existing.get(0)).path("tokens").asInt()!=tokens)throw invalid("模型用量记录不一致");return null;}
            jdbc.update("INSERT INTO agent_task_step(id,task_id,task_version,kind,detail_json,created_at) VALUES(?,?,?,'MODEL_USAGE',?,?)",event,id,row.view.version(),encode(Map.of("call",call,"tokens",tokens)),LocalDateTime.now());return null;
        });
    }
    public List<View> list(Session me){return jdbc.query("SELECT * FROM agent_task WHERE user_id=? AND session_hash=? AND role=? AND venue_id=? ORDER BY updated_at DESC LIMIT 30",(r,i)->view(r),me.userId(),me.sessionKey(),me.role(),me.venueId());}
    public View check(Session me,String id,long expected,String document){var row=owned(me,id,false);checkVersion(row,expected,document);return row.view;}
    public View update(Session me,String id,long expected,String document,String status,Object plan,Object checkpoint){
        if(!STATES.contains(status))throw invalid("任务状态不受支持");
        String p=encode(plan),c=encode(checkpoint);
        return tx.execute(ignored->{var row=owned(me,id,true);checkVersion(row,expected,document);if(TERMINAL.contains(row.view.status()))throw invalid("任务已经结束");
            int changed=jdbc.update("UPDATE agent_task SET version=version+1,status=?,plan_json=?,checkpoint_json=?,updated_at=? WHERE id=? AND version=?",status,p,c,LocalDateTime.now(),id,expected);
            if(changed!=1)throw invalid("任务状态已变化，请重新读取");
            jdbc.update("INSERT INTO agent_task_step(id,task_id,task_version,kind,detail_json,created_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),id,expected+1,status,c,LocalDateTime.now());
            return get(me,id);
        });
    }
    public View revise(Session me,String id,long expected,String document,String amendment){
        validGoal(amendment);
        return tx.execute(ignored->{var row=owned(me,id,true);checkVersion(row,expected,document);if(TERMINAL.contains(row.view.status()))throw invalid("任务已经结束");
            String goal=AgentGoalRevision.apply(row.view.goal(),amendment);validGoal(goal);
            jdbc.update("UPDATE agent_task SET version=version+1,goal=?,plan_json='{}',status='RUNNING',updated_at=? WHERE id=? AND version=?",goal,LocalDateTime.now(),id,expected);return get(me,id);
        });
    }
    public View cancel(Session me,String id,long expected,String document){var row=check(me,id,expected,document);if(TERMINAL.contains(row.status()))return row;return update(me,id,expected,document,"CANCELLED",row.plan(),row.checkpoint());}
    public View recover(Session me,String id,long expected,String newDocument){
        validDocument(newDocument);
        return tx.execute(ignored->{var row=owned(me,id,true);if(row.view.version()!=expected||TERMINAL.contains(row.view.status()))throw invalid("任务已变化或结束");
            jdbc.update("UPDATE agent_task SET version=version+1,document_nonce=?,status='PAUSED',updated_at=? WHERE id=? AND version=?",newDocument,LocalDateTime.now(),id,expected);return get(me,id);
        });
    }
    public View pause(Session me,String id,String document){return tx.execute(ignored->{var row=owned(me,id,true);checkVersion(row,row.view.version(),document);if(TERMINAL.contains(row.view.status()))return row.view;if("PAUSED".equals(row.view.status()))return row.view;return update(me,id,row.view.version(),document,"PAUSED",row.view.plan(),row.view.checkpoint());});}
    private Row owned(Session me,String id,boolean lock){
        if(id==null||!id.matches("[a-f0-9-]{36}"))throw denied();
        var rows=jdbc.query("SELECT * FROM agent_task WHERE id=?"+(lock?" FOR UPDATE":""),(r,i)->new Row(view(r),r.getLong("user_id"),r.getLong("venue_id"),r.getString("role"),r.getString("session_hash"),r.getString("document_nonce")),id);
        if(rows.size()!=1)throw denied();var row=rows.get(0);
        if(row.user!=me.userId()||row.venue!=me.venueId()||!row.role.equals(me.role())||!row.session.equals(me.sessionKey()))throw denied();
        int tokens=0;for(var detail:jdbc.queryForList("SELECT detail_json FROM agent_task_step WHERE task_id=? AND kind='MODEL_USAGE'",String.class,id))tokens+=decode(detail).path("tokens").asInt();
        var checkpoint=row.view.checkpoint().isObject()?(com.fasterxml.jackson.databind.node.ObjectNode)row.view.checkpoint().deepCopy():json.createObjectNode();checkpoint.put("totalTokens",Math.max(tokens,checkpoint.path("totalTokens").asInt()));
        var view=row.view;return new Row(new View(view.id(),view.version(),view.goal(),view.status(),view.plan(),checkpoint,view.createdAt(),view.updatedAt()),row.user,row.venue,row.role,row.session,row.document);
    }
    private void checkVersion(Row row,long expected,String document){if(row.view.version()!=expected||!row.document.equals(document))throw invalid("任务版本或当前网页租约已失效，请重新读取或恢复");}
    private View view(java.sql.ResultSet r)throws java.sql.SQLException{return new View(r.getString("id"),r.getLong("version"),r.getString("goal"),r.getString("status"),decode(r.getString("plan_json")),decode(r.getString("checkpoint_json")),r.getTimestamp("created_at").toLocalDateTime(),r.getTimestamp("updated_at").toLocalDateTime());}
    private JsonNode decode(String s){try{return json.readTree(s);}catch(Exception e){throw new IllegalStateException("无法读取任务状态");}}
    private String encode(Object value){try{String s=json.writeValueAsString(value);if(s.length()>16000)throw invalid("任务状态过长");return s;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw invalid("任务状态格式不正确");}}
    private void validDocument(String s){if(s==null||!s.matches("[A-Za-z0-9_-]{8,128}"))throw invalid("网页租约格式不正确");}
    private void validGoal(String s){if(s==null||s.isBlank()||s.length()>2000)throw invalid("任务目标须为1至2000字");if(s.matches("(?s).*(?:sk-[A-Za-z0-9_-]{15,}|eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+).*"))throw invalid("任务中含密钥或登录令牌，请移除凭据后重新描述业务目标");}
}
