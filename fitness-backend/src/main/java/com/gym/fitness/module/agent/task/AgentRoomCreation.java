package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.regex.Pattern;
import static com.gym.fitness.module.agent.AgentIdentity.invalid;

/** Narrow room creation contract. Filling a form never proves that a room was saved. */
final class AgentRoomCreation {
    static final String PATH="/api/v1/admin/rooms";
    private AgentRoomCreation(){}
    static boolean target(String method,String path){return "POST".equals(method)&&PATH.equals(path);}
    static String clause(String goal){return AgentGoalClauses.parse(goal).stream().filter(c->AgentOperationIntent.unquoted(c).matches(".*(?:新增|新建|创建|添加).*教室.*")).findFirst().orElseThrow(()->invalid("当前任务没有新增教室目标"));}
    static void guard(String goal,JsonNode before,JsonNode body){
        String c=clause(goal);
        var name=Pattern.compile("(?:名称|名字)(?:为|是|叫做|叫|[:：])?\\s*[‘“\"「]?([^，,。；;‘’“”\"「」]+)").matcher(c);
        if(!name.find()||!body.path("name").isTextual()||!name.group(1).trim().equals(body.path("name").asText()))throw invalid("请提供准确教室名称，拟提交名称须与目标一致");
        var capacity=Pattern.compile("(?:可容纳|容量|人数)(?:为|是|[:：])?\\s*(\\d+)(?:人)?").matcher(c);
        int expected=capacity.find()?Integer.parseInt(capacity.group(1)):20;
        if(!body.path("capacity").isIntegralNumber()||!body.path("capacity").canConvertToInt()||body.path("capacity").asInt()!=expected)throw invalid("拟提交可容纳人数与用户要求不一致");
        var venue=Pattern.compile("(?:所属场馆|所属门店|场馆|门店)(?:为|是|[:：])?\\s*[‘“\"「]?([^，,。；;‘’“”\"「」]+)").matcher(c);
        if(venue.find()){
            String label=venue.group(1).trim(),actual=before.path("venueName").asText();
            if(!label.equals(actual)&&!label.equals(actual.replaceFirst("(?i)^FitChain\\s*","")))throw invalid("所属场馆与用户要求不一致");
            if(before.path("venueNameCount").asInt()!=1)throw invalid("存在同名场馆，请先核对准确门店；不会猜测归属");
        }else if(!before.path("actorVenue").asBoolean())throw invalid("未指定其他场馆，只能在当前门店新增教室");
        var status=Pattern.compile("状态(?:为|是|[:：])?\\s*(启用|停用)").matcher(c);
        String expectedStatus=status.find()&&"停用".equals(status.group(1))?"INACTIVE":"ACTIVE";
        if(!expectedStatus.equals(body.path("status").asText("ACTIVE")))throw invalid("未授权改变教室状态");
        var note=Pattern.compile("备注(?:为|是|[:：])?\\s*[‘“\"「]?([^，,。；;‘’“”\"「」]+)").matcher(c);
        if(!body.path("note").asText("").trim().equals(note.find()?note.group(1).trim():""))throw invalid("教室备注与任务要求不一致");
        body.fieldNames().forEachRemaining(k->{if(!Set.of("name","venueId","capacity","status","note").contains(k))throw invalid("新增教室包含未注册字段");});
    }
}
